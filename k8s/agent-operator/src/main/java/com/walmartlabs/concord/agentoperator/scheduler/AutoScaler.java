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
import com.walmartlabs.concord.agentoperator.processqueue.ProcessQueueEntry;
import com.walmartlabs.concord.common.MapMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class AutoScaler {

    private static final Logger log = LoggerFactory.getLogger(AutoScaler.class);

    private final Function<String, Integer> podCounter;
    private final Function<AgentPoolInstance, Boolean> canBeUpdated;

    public AutoScaler(Function<String, Integer> podCounter) {
        this(podCounter, i -> {
            long t = System.currentTimeMillis();
            return t - i.getLastUpdateTimestamp() > i.getResource().getSpec().getScalingDelayMs();
        });
    }

    public AutoScaler(Function<String, Integer> podCounter, Function<AgentPoolInstance, Boolean> canBeUpdated) {
        this.podCounter = podCounter;
        this.canBeUpdated = canBeUpdated;
    }

    public AgentPoolInstance apply(AgentPoolInstance i, List<ProcessQueueEntry> queueEntries) {
        AgentPoolConfiguration cfg = i.getResource().getSpec();

        if (!canBeUpdated.apply(i)) {
            // was updated recently, skipping
            return i;
        }

        // the number of processes waiting for an agent in the current pool
        int enqueuedCount = (int) queueEntries.stream()
                .map(ProcessQueueEntry::getRequirements)
                .filter(Objects::nonNull)
                .filter(a -> MapMatcher.matches(a, cfg.getQueueSelector()))
                .count();

        // count the currently running pods
        int podsCount = podCounter.apply(i.getName());

        int increment = 0;
        if (enqueuedCount >= podsCount) {
            increment = cfg.getSizeIncrement();
        } if (enqueuedCount < podsCount) {
            increment = -cfg.getSizeIncrement();
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
        return AgentPoolInstance.updateTargetSize(i, targetSize);
    }
}
