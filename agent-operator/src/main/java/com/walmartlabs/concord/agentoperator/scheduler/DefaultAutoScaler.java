package com.walmartlabs.concord.agentoperator.scheduler;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.agentoperator.crd.AgentPoolConfiguration;
import com.walmartlabs.concord.agentoperator.processqueue.ProcessQueueClient;
import com.walmartlabs.concord.agentoperator.processqueue.ProcessQueueEntry;
import com.walmartlabs.concord.common.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class DefaultAutoScaler implements AutoScaler {

    public static final String NAME = "default";

    private static final Logger log = LoggerFactory.getLogger(DefaultAutoScaler.class);

    private final ProcessQueueClient processQueueClient;
    private final Function<String, Integer> podCounter;
    private final Function<AgentPoolInstance, Boolean> canBeScaledUp;
    private final Function<AgentPoolInstance, Boolean> canBeScaledDown;
    private long scaleUpTimeStamp;
    private long scaleDownTimeStamp;

    public DefaultAutoScaler(ProcessQueueClient processQueueClient, Function<String, Integer> podCounter) {
        this(processQueueClient, podCounter, i -> {
            long t = System.currentTimeMillis();
            return t - i.getLastScaleUpTimestamp() > i.getResource().getSpec().getScaleUpDelayMs();
        }, i -> {
            long t = System.currentTimeMillis();
            return t - i.getLastScaleDownTimeStamp() > i.getResource().getSpec().getScaleDownDelayMs();
        });
    }

    public DefaultAutoScaler(ProcessQueueClient processQueueClient,
                             Function<String, Integer> podCounter, Function<AgentPoolInstance, Boolean> canBeScaledUp,
                             Function<AgentPoolInstance, Boolean> canBeScaledDown) {
        this.processQueueClient = processQueueClient;
        this.podCounter = podCounter;
        this.canBeScaledUp = canBeScaledUp;
        this.canBeScaledDown = canBeScaledDown;
        this.scaleUpTimeStamp = System.currentTimeMillis();
        this.scaleDownTimeStamp = System.currentTimeMillis();
    }

    /**
     * Scale up or Scale down the number of agent pods depending on various conditions
     * <p>
     * If enqueued process count is greater than the threshold defined for incrementing to max
     * pool size, increase the pool size to the maximum size.
     * Otherwise, if the enqueued process count is greater than the threshold defined for incrementing
     * the pool size, increase the pool size by the increment percentage defined
     * <p>
     * Decrease the pool size by the decrement percentage defined only if,
     * - enqueued process count is lesser than the minimum pool size threshold defined
     * - running process count is lesser than the threshold defined (which depends on current size of the pool
     * running the processes, and a constant factor specified - default to 1. Simplified to
     * runningCount < podsCount)
     *
     * @param i            Agent pool on which the scaling activity is to be performed
     */
    public AgentPoolInstance apply(AgentPoolInstance i) throws IOException {
        int queueQueryLimit = i.getResource().getSpec().getQueueQueryLimit();
        QueueSelector queueSelector = QueueSelector.parse(i.getResource().getSpec().getQueueSelector());
        List<ProcessQueueEntry> queueEntries = processQueueClient.query("ENQUEUED", queueQueryLimit, queueSelector);

        scaleUpTimeStamp = i.getLastScaleUpTimestamp();
        scaleDownTimeStamp = i.getLastScaleDownTimeStamp();

        AgentPoolConfiguration cfg = i.getResource().getSpec();

        if (!canBeScaledUp.apply(i) && !canBeScaledDown.apply(i)) {
            // was updated recently, skipping
            return i;
        }

        // count the currently running pods
        int podsCount = podCounter.apply(i.getName());
        log.info("['{}']: Current pool size: {}", i.getName(), podsCount);

        // the number of processes waiting for an agent in the current pool
        int enqueuedCount = getProcessCount(cfg, queueEntries);
        log.info("['{}']: Enqueued process count: {}", i.getName(), enqueuedCount);

        if (podsCount < cfg.getMinSize()) {
            return AgentPoolInstance.updateTargetSize(i, cfg.getMinSize(), System.currentTimeMillis(), System.currentTimeMillis());
        }

        // The threshold above which the operator can scale up the agent pods to the defined maximum pool size
        double maxPoolSizeThreshold = cfg.getMaxSize() * cfg.getIncrementThresholdFactor();

        // The threshold above which the pool size can be increased by the increment percentage defined
        double incrementThreshold = cfg.getIncrementThresholdFactor() * podsCount;

        // The threshold, combined with threshold for running processes determine if the pool size can be
        // reduced by the decrement percentage defined
        double minPoolSizeThreshold = cfg.getDecrementThresholdFactor() * cfg.getMinSize();

        // Initial target size of the agent pool before updation
        int targetSize = i.getTargetSize();

        // Try scaling up if the time elapsed after last scale up operation
        // is greater than the scale up delay defined (default: 15s)
        if (canBeScaledUp.apply(i)) {
            targetSize = tryScaleUp(cfg, i, podsCount, enqueuedCount, targetSize, maxPoolSizeThreshold, incrementThreshold);

            // Reset scaledown delay counter if enqueued count is greater than min threshold.
            // Scale down should happen only if enqueued count is less than
            // min threshold consistently for scaledown delay defined (default: 180s)
            if (enqueuedCount >= minPoolSizeThreshold) {
                log.info("['{}']: Resetting scale down delay counter - (enqueued count({}) >= minimum threshold({}))...",
                        i.getName(), enqueuedCount, minPoolSizeThreshold);
                scaleDownTimeStamp = System.currentTimeMillis();
            }
        }

        // Try scaling down if the time elapsed after last scale down operation
        // is greater than the scale down delay defined (default: 180s)
        if (canBeScaledDown.apply(i)) {
            targetSize = tryScaleDown(cfg, i, podsCount, enqueuedCount, targetSize, minPoolSizeThreshold);
        }

        if (targetSize == i.getTargetSize()) {
            log.info("['{}']: Not changing the pool size.", i.getName());
        } else {
            log.info("apply ['{}'] -> updated to {}", i.getName(), targetSize);
        }
        return AgentPoolInstance.updateTargetSize(i, targetSize, scaleUpTimeStamp, scaleDownTimeStamp);
    }

    private int tryScaleUp(AgentPoolConfiguration cfg, AgentPoolInstance i, int podsCount, int enqueuedCount, int poolSize,
                           double maxPoolSizeThreshold, double incrementThreshold) {

        // To prevent scale up before previous scale down action is completed
        podsCount = Math.min(podsCount, i.getTargetSize());

        // Reset scaleup delay counter for every attempt to scale up
        scaleUpTimeStamp = System.currentTimeMillis();

        if (podsCount < cfg.getMaxSize()) {
            if (enqueuedCount >= maxPoolSizeThreshold) {
                poolSize = cfg.getMaxSize();
                log.info("['{}']: Incrementing to max size - {}", i.getName(), poolSize);
            } else if (enqueuedCount >= incrementThreshold) {
                poolSize = (int) Math.round(podsCount * (1 + cfg.getPercentIncrement() / 100));

                // Limit to maximum pool size if the computed target size is more than the max size
                if (poolSize > cfg.getMaxSize()) {
                    log.warn("['{}']: Target pool size exceeds the allowed maximum: {} > {}. Updating to maximum size - {}",
                            i.getName(), poolSize, cfg.getMaxSize(), cfg.getMaxSize());
                    poolSize = cfg.getMaxSize();
                }

                log.info("['{}']: Scaling up to {}...", i.getName(), poolSize);
            }
        } else {
            log.warn("['{}']: Target pool size already the allowed maximum size: {}. Not updating.",
                    i.getName(), cfg.getMaxSize());
        }

        return poolSize;
    }

    private int tryScaleDown(AgentPoolConfiguration cfg, AgentPoolInstance i, int podsCount, int enqueuedCount,
                             int poolSize, double minPoolSizeThreshold) {

        // To prevent scale down before previous scale up action is completed
        podsCount = Math.max(podsCount, i.getTargetSize());

        // Reset scaledown delay counter for every attempt to scale down
        scaleDownTimeStamp = System.currentTimeMillis();

        if (podsCount > cfg.getMinSize()) {
            if (enqueuedCount < minPoolSizeThreshold) {
                poolSize = (int) Math.floor(podsCount * (1 - cfg.getPercentDecrement() / 100));

                log.info("['{}']: Scaling down - (enqueued count({}) < minimum threshold({})) for more than {} seconds...",
                        i.getName(), enqueuedCount, minPoolSizeThreshold,
                        (i.getResource().getSpec().getScaleDownDelayMs() / 1000));

                // Limit to minimum pool size if the computed target size is less than the min size
                if (poolSize < cfg.getMinSize()) {
                    log.warn("['{}']: Target pool size lesser than the allowed minimum: {} < {}. Updating to minimum size - {}",
                            i.getName(), poolSize, cfg.getMinSize(), cfg.getMinSize());
                    poolSize = cfg.getMinSize();
                } else {
                    log.info("['{}']: Scaling down to {}...", i.getName(), poolSize);
                }
            }
        } else {
            log.warn("['{}']: Target pool size already the allowed minimum size: {}. Not updating.",
                    i.getName(), cfg.getMinSize());
        }

        return poolSize;
    }

    private static int getProcessCount(AgentPoolConfiguration cfg, List<ProcessQueueEntry> processQueueEntries) {
        return (int) processQueueEntries.stream()
                .map(ProcessQueueEntry::getRequirements)
                .filter(Objects::nonNull)
                .filter(a -> isEmpty(cfg.getQueueSelector()) || Matcher.matches(a, cfg.getQueueSelector()))
                .count();
    }

    private static boolean isEmpty(Map<String, Object> m) {
        return m == null || m.isEmpty();
    }
}
