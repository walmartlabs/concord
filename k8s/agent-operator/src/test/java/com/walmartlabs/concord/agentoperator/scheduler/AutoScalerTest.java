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
import com.walmartlabs.concord.agentoperator.processqueue.ProcessQueueEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AutoScalerTest {

    @Test
    public void testStill() {
        AtomicInteger podCount = new AtomicInteger(1);

        AutoScaler as = new AutoScaler(n -> podCount.get(), i -> true, i -> true);

        AgentPoolConfiguration spec = new AgentPoolConfiguration();
        spec.setPercentIncrement(50);
        spec.setDecrementThresholdFactor(1.0);
        spec.setIncrementThresholdFactor(1.5);
        spec.setPercentDecrement(10);
        spec.setQueueSelector(Collections.singletonMap("test", 123));

        AgentPool resource = new AgentPool();
        resource.setSpec(spec);

        AgentPoolInstance pool = new AgentPoolInstance("test", resource, AgentPoolInstance.Status.ACTIVE, 1, 0, 0, 0);

        List<ProcessQueueEntry> queue = new ArrayList<>();

        // ---

        pool = as.apply(pool, queue);
        assertEquals(1, pool.getTargetSize());

        pool = as.apply(pool, queue);
        assertEquals(1, pool.getTargetSize());
    }

    @Test
    public void testZeroStart() {
        AtomicInteger podCount = new AtomicInteger(0);

        AutoScaler as = new AutoScaler(n -> podCount.get(), i -> true, i -> true);

        AgentPoolConfiguration spec = new AgentPoolConfiguration();
        spec.setPercentIncrement(50);
        spec.setDecrementThresholdFactor(1.0);
        spec.setIncrementThresholdFactor(1.5);
        spec.setPercentDecrement(10);
        spec.setQueueSelector(Collections.singletonMap("test", 123));

        AgentPool resource = new AgentPool();
        resource.setSpec(spec);

        AgentPoolInstance pool = new AgentPoolInstance("test", resource, AgentPoolInstance.Status.ACTIVE, 1, 0, 0, 0);

        List<ProcessQueueEntry> queue = new ArrayList<>();

        // ---

        pool = as.apply(pool, queue);
        assertEquals(1, pool.getTargetSize());

        podCount.set(2);

        pool = as.apply(pool, queue);
        assertEquals(1, pool.getTargetSize());
    }

    @Test
    public void testQueue() {
        AtomicInteger podCount = new AtomicInteger(1);

        AutoScaler as = new AutoScaler(n -> podCount.get(), i -> true, i -> true);

        AgentPoolConfiguration spec = new AgentPoolConfiguration();
        spec.setPercentIncrement(50);
        spec.setDecrementThresholdFactor(1.0);
        spec.setIncrementThresholdFactor(1.5);
        spec.setPercentDecrement(10);
        spec.setQueueSelector(Collections.singletonMap("test", 123));

        AgentPool resource = new AgentPool();
        resource.setSpec(spec);

        AgentPoolInstance pool = new AgentPoolInstance("test", resource, AgentPoolInstance.Status.ACTIVE, 1, 0, 0, 0);

        List<ProcessQueueEntry> queue = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            queue.add(new ProcessQueueEntry(Collections.singletonMap("test", 123)));
        }

        // ---

        pool = as.apply(pool, queue);
        assertEquals(2, pool.getTargetSize());

        podCount.set(2);

        pool = as.apply(pool, queue);
        assertEquals(3, pool.getTargetSize());

        podCount.set(3);

        pool = as.apply(pool, queue);
        assertEquals(5, pool.getTargetSize());

        podCount.set(5);

        pool = as.apply(pool, queue);
        assertEquals(8, pool.getTargetSize());

        podCount.set(8);

        // ---

        queue.clear();

        pool = as.apply(pool, queue);
        assertEquals(7, pool.getTargetSize());

        podCount.set(7);

        pool = as.apply(pool, queue);
        assertEquals(6, pool.getTargetSize());
    }

    @Test
    public void testQueueFromZero() {
        AtomicInteger podCount = new AtomicInteger(1);

        AutoScaler as = new AutoScaler(n -> podCount.get(), i -> true, i -> true);

        AgentPoolConfiguration spec = new AgentPoolConfiguration();
        spec.setSize(0);
        spec.setMinSize(0);
        spec.setPercentIncrement(50);
        spec.setDecrementThresholdFactor(1.0);
        spec.setIncrementThresholdFactor(1.5);
        spec.setPercentDecrement(10);
        spec.setQueueSelector(Collections.singletonMap("test", 123));

        AgentPool resource = new AgentPool();
        resource.setSpec(spec);

        AgentPoolInstance pool = new AgentPoolInstance("test", resource, AgentPoolInstance.Status.ACTIVE, 0, 0, 0, 0);

        List<ProcessQueueEntry> queue = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            queue.add(new ProcessQueueEntry(Collections.singletonMap("test", 123)));
        }

        // ---

        pool = as.apply(pool, queue);
        assertEquals(1, pool.getTargetSize());

        podCount.set(1);

        pool = as.apply(pool, queue);
        assertEquals(2, pool.getTargetSize());

        podCount.set(2);

        pool = as.apply(pool, queue);
        assertEquals(3, pool.getTargetSize());

        podCount.set(3);

        pool = as.apply(pool, queue);
        assertEquals(5, pool.getTargetSize());

        podCount.set(5);

        // ---

        queue.clear();

        pool = as.apply(pool, queue);
        assertEquals(4, pool.getTargetSize());

        podCount.set(4);

        pool = as.apply(pool, queue);
        assertEquals(3, pool.getTargetSize());

        podCount.set(3);

        pool = as.apply(pool, queue);
        assertEquals(2, pool.getTargetSize());

        podCount.set(2);

        pool = as.apply(pool, queue);
        assertEquals(1, pool.getTargetSize());

        podCount.set(1);

        pool = as.apply(pool, queue);
        assertEquals(0, pool.getTargetSize());
    }
}
