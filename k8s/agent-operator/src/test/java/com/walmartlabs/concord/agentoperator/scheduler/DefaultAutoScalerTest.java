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

import com.walmartlabs.concord.agentoperator.crd.AgentPool;
import com.walmartlabs.concord.agentoperator.crd.AgentPoolConfiguration;
import com.walmartlabs.concord.agentoperator.processqueue.ProcessQueueClient;
import com.walmartlabs.concord.agentoperator.processqueue.ProcessQueueEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultAutoScalerTest {

    @Test
    public void testStill() throws Exception {
        AtomicInteger podCount = new AtomicInteger(1);
        List<ProcessQueueEntry> queue = new ArrayList<>();

        DefaultAutoScaler as = new DefaultAutoScaler(mockProcessQueueClient(queue), n -> podCount.get(), i -> true, i -> true);

        AgentPoolConfiguration spec = new AgentPoolConfiguration();
        spec.setPercentIncrement(50);
        spec.setDecrementThresholdFactor(1.0);
        spec.setIncrementThresholdFactor(1.5);
        spec.setPercentDecrement(10);
        spec.setQueueSelector(Collections.singletonMap("test", 123));

        AgentPool resource = new AgentPool();
        resource.setSpec(spec);

        AgentPoolInstance pool = new AgentPoolInstance("test", resource, AgentPoolInstance.Status.ACTIVE, 1, 0, 0, 0);

        // ---

        pool = as.apply(pool);
        assertEquals(1, pool.getTargetSize());

        pool = as.apply(pool);
        assertEquals(1, pool.getTargetSize());
    }

    @Test
    public void testZeroStart() throws IOException {
        AtomicInteger podCount = new AtomicInteger(0);
        List<ProcessQueueEntry> queue = new ArrayList<>();

        DefaultAutoScaler as = new DefaultAutoScaler(mockProcessQueueClient(queue), n -> podCount.get(), i -> true, i -> true);

        AgentPoolConfiguration spec = new AgentPoolConfiguration();
        spec.setPercentIncrement(50);
        spec.setDecrementThresholdFactor(1.0);
        spec.setIncrementThresholdFactor(1.5);
        spec.setPercentDecrement(10);
        spec.setQueueSelector(Collections.singletonMap("test", 123));

        AgentPool resource = new AgentPool();
        resource.setSpec(spec);

        AgentPoolInstance pool = new AgentPoolInstance("test", resource, AgentPoolInstance.Status.ACTIVE, 1, 0, 0, 0);

        // ---

        pool = as.apply(pool);
        assertEquals(1, pool.getTargetSize());

        podCount.set(2);

        pool = as.apply(pool);
        assertEquals(1, pool.getTargetSize());
    }

    private ProcessQueueClient mockProcessQueueClient(List<ProcessQueueEntry> queue) {
        return new ProcessQueueClient("test", "test") {
            @Override
            public List<ProcessQueueEntry> query(String processStatus, int limit, QueueSelector queueSelector) throws IOException {
                return queue;
            }
        };
    }

    @Test
    public void testQueue() throws IOException {
        AtomicInteger podCount = new AtomicInteger(1);
        List<ProcessQueueEntry> queue = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            queue.add(new ProcessQueueEntry(Collections.singletonMap("test", 123)));
        }

        DefaultAutoScaler as = new DefaultAutoScaler(mockProcessQueueClient(queue), n -> podCount.get(), i -> true, i -> true);

        AgentPoolConfiguration spec = new AgentPoolConfiguration();
        spec.setPercentIncrement(50);
        spec.setDecrementThresholdFactor(1.0);
        spec.setIncrementThresholdFactor(1.5);
        spec.setPercentDecrement(10);
        spec.setQueueSelector(Collections.singletonMap("test", 123));

        AgentPool resource = new AgentPool();
        resource.setSpec(spec);

        AgentPoolInstance pool = new AgentPoolInstance("test", resource, AgentPoolInstance.Status.ACTIVE, 1, 0, 0, 0);

        // ---

        pool = as.apply(pool);
        assertEquals(2, pool.getTargetSize());

        podCount.set(2);

        pool = as.apply(pool);
        assertEquals(3, pool.getTargetSize());

        podCount.set(3);

        pool = as.apply(pool);
        assertEquals(5, pool.getTargetSize());

        podCount.set(5);

        pool = as.apply(pool);
        assertEquals(8, pool.getTargetSize());

        podCount.set(8);

        // ---

        queue.clear();

        pool = as.apply(pool);
        assertEquals(7, pool.getTargetSize());

        podCount.set(7);

        pool = as.apply(pool);
        assertEquals(6, pool.getTargetSize());
    }
}
