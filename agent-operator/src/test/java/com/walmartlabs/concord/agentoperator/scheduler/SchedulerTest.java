package com.walmartlabs.concord.agentoperator.scheduler;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchedulerTest {

    @Test
    void keepsPoolsSeparatedByNamespace() throws Exception {
        var scheduler = new Scheduler(null, null, false);
        var onAdd = Scheduler.class.getDeclaredMethod("onAdd", AgentPool.class);
        onAdd.setAccessible(true);

        onAdd.invoke(scheduler, agentPool("concord-ns-a", "shared-pool"));
        onAdd.invoke(scheduler, agentPool("concord-ns-b", "shared-pool"));

        assertEquals(2, pools(scheduler).size());
    }

    @Test
    void deletesOnlyMatchingNamespacedPool() throws Exception {
        var scheduler = new Scheduler(null, null, false);
        var onAdd = Scheduler.class.getDeclaredMethod("onAdd", AgentPool.class);
        onAdd.setAccessible(true);
        var onDelete = Scheduler.class.getDeclaredMethod("onDelete", AgentPool.class);
        onDelete.setAccessible(true);

        onAdd.invoke(scheduler, agentPool("concord-ns-a", "shared-pool"));
        onAdd.invoke(scheduler, agentPool("concord-ns-b", "shared-pool"));
        onDelete.invoke(scheduler, agentPool("concord-ns-a", "shared-pool"));

        assertEquals(2, pools(scheduler).size());
        assertEquals(AgentPoolInstance.Status.DELETED, pools(scheduler).get("concord-ns-a/shared-pool").getStatus());
        assertEquals(AgentPoolInstance.Status.ACTIVE, pools(scheduler).get("concord-ns-b/shared-pool").getStatus());
    }

    private static AgentPool agentPool(String namespace, String name) {
        var result = new AgentPool();
        result.setMetadata(new ObjectMeta());
        result.getMetadata().setNamespace(namespace);
        result.getMetadata().setName(name);

        var spec = new AgentPoolConfiguration();
        spec.setSize(1);
        result.setSpec(spec);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, AgentPoolInstance> pools(Scheduler scheduler) throws Exception {
        Field pools = Scheduler.class.getDeclaredField("pools");
        pools.setAccessible(true);
        return (Map<String, AgentPoolInstance>) pools.get(scheduler);
    }
}
