package com.walmartlabs.concord.agentoperator.scheduler;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.common.ConfigurationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

public class LinearAutoScaler implements AutoScaler {

    private static final Logger log = LoggerFactory.getLogger(LinearAutoScaler.class);

    public static final String NAME = "linear";

    private final ProcessQueueClient processQueueClient;
    private final Function<String, Integer> podCounter;
    private final Function<AgentPoolInstance, Boolean> canBeScaledUp;
    private final Function<AgentPoolInstance, Boolean> canBeScaledDown;

    public LinearAutoScaler(ProcessQueueClient processQueueClient, Function<String, Integer> podCounter) {
        this(processQueueClient, podCounter, i -> {
            long t = System.currentTimeMillis();
            return t - i.getLastScaleUpTimestamp() > i.getResource().getSpec().getScaleUpDelayMs();
        }, i -> {
            long t = System.currentTimeMillis();
            return t - i.getLastScaleDownTimeStamp() > i.getResource().getSpec().getScaleDownDelayMs();
        });
    }

    public LinearAutoScaler(ProcessQueueClient processQueueClient,
                             Function<String, Integer> podCounter, Function<AgentPoolInstance, Boolean> canBeScaledUp,
                             Function<AgentPoolInstance, Boolean> canBeScaledDown) {
        this.processQueueClient = processQueueClient;
        this.podCounter = podCounter;
        this.canBeScaledUp = canBeScaledUp;
        this.canBeScaledDown = canBeScaledDown;
    }

    @Override
    public AgentPoolInstance apply(AgentPoolInstance i) throws IOException {
        if (!canBeScaledUp.apply(i) && !canBeScaledDown.apply(i)) {
            // was updated recently, skipping
            return i;
        }

        long scaleUpTimeStamp = i.getLastScaleUpTimestamp();
        long scaleDownTimeStamp = i.getLastScaleDownTimeStamp();

        AgentPoolConfiguration cfg = i.getResource().getSpec();

        String flavor = (String) ConfigurationUtils.get(cfg.getQueueSelector(), "agent", "flavor");
        List<ProcessQueueEntry> queueEntries = processQueueClient.query("ENQUEUED", cfg.getMaxSize(), flavor);

        // count the currently running pods
        int podsCount = podCounter.apply(i.getName());

        // the number of processes waiting for an agent in the current pool
        int enqueuedCount = queueEntries.size();

        int increment = 0;
        if (enqueuedCount > podsCount) {
            increment = cfg.getSizeIncrement();
            scaleUpTimeStamp = System.currentTimeMillis();
        } if (enqueuedCount < podsCount) {
            increment = -cfg.getSizeIncrement();
            scaleDownTimeStamp = System.currentTimeMillis();
        }

        int targetSize = Math.max(cfg.getMinSize(), podsCount + increment);
        if (i.getTargetSize() == targetSize) {
            // no changes needed
            return i;
        }

        if (targetSize > cfg.getMaxSize()) {
            log.warn("apply ['{}'] -> target pool size exceeds the allowed maximum: {} > {}", i.getName(), enqueuedCount, cfg.getMaxSize());
        }

        targetSize = Math.min(targetSize, cfg.getMaxSize());
        log.info("apply ['{}'] -> updated to {}", i.getName(), targetSize);
        return AgentPoolInstance.updateTargetSize(i, targetSize, scaleUpTimeStamp, scaleDownTimeStamp);
    }
}
