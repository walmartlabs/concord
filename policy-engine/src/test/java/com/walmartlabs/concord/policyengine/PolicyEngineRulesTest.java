package com.walmartlabs.concord.policyengine;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PolicyEngineRulesTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    public void serializationTest() throws Exception {
        DependencyRule r1 = new DependencyRule("msg1", "schema1", "groupId1", "artifactId1", "fromVersion1", "toVersion1");
        DependencyRule r2 = new DependencyRule("msg2", "schema2", "groupId2", "artifactId2", "fromVersion2", "toVersion2");
        DependencyRule r3 = new DependencyRule("msg3", "schema3", "groupId3", "artifactId3", "fromVersion3", "toVersion3");
        PolicyRules<DependencyRule> dependencyRules = new PolicyRules<>(Collections.singletonList(r1), Collections.singletonList(r2), Collections.singletonList(r3));

        FileRule f1 = new FileRule("msg1", "1K", "FILE", Collections.singletonList("name1"));
        FileRule f2 = new FileRule("msg2", "2K", "DIR", Collections.singletonList("name2"));
        FileRule f3 = new FileRule("msg3", "3K", "FILE", Collections.singletonList("name3"));
        PolicyRules<FileRule> fileRules = new PolicyRules<>(Collections.singletonList(f1), Collections.singletonList(f2), Collections.singletonList(f3));

        TaskRule t1 = new TaskRule("msg1", "taskName1", "methodName1", Collections.singletonList(new TaskRule.Param(1, "name1", true, Collections.singletonList("values"))), Collections.singletonList(new TaskRule.TaskResult("task1", "name", null)));
        TaskRule t2 = new TaskRule("msg2", "taskName2", "methodName2", Collections.singletonList(new TaskRule.Param(2, "name1", true, Collections.singletonList("values"))), null);
        TaskRule t3 = new TaskRule("msg3", "taskName3", "methodName3", Collections.singletonList(new TaskRule.Param(3, "name1", true, Collections.singletonList("values"))), null);
        PolicyRules<TaskRule> taskRules = new PolicyRules<>(Collections.singletonList(t1), Collections.singletonList(t2), Collections.singletonList(t3));

        WorkspaceRule workspaceRule = new WorkspaceRule("msg", 12345L, Collections.singleton("a"));

        ContainerRule containerRules = new ContainerRule("msg1", "maxRam", 2);

        ConcurrentProcessRule concurrent = new ConcurrentProcessRule("msg1", 23, 433);
        ForkDepthRule forkDepthRule = new ForkDepthRule("msg1", 12);
        ProcessTimeoutRule processTimeoutRule = new ProcessTimeoutRule("msg1", "13");
        QueueRule queueRules = new QueueRule(concurrent, forkDepthRule, processTimeoutRule);

        ProtectedTasksRule protectedTasksRules = new ProtectedTasksRule(Collections.singleton("task1"));

        EntityRule e1 = new EntityRule("msg1", "entity1", "action1", Collections.singletonMap("k1", "v"));
        EntityRule e2 = new EntityRule("msg2", "entity2", "action2", Collections.singletonMap("k2", "v"));
        EntityRule e3 = new EntityRule("msg3", "entity3", "action3", Collections.singletonMap("k3", "v"));
        PolicyRules<EntityRule> entityRules = new PolicyRules<>(Collections.singletonList(e1), Collections.singletonList(e2), Collections.singletonList(e3));

        Map<String, Object> processCfg = new HashMap<>();
        processCfg.put("a", "b");

        JsonStoreRule.StoreRule storageRule = new JsonStoreRule.StoreRule(123, "storage-rule");
        JsonStoreRule.StoreDataRule storageDataRule = new JsonStoreRule.StoreDataRule(11L, "storage-data-rule");

        Map<String, Object> defaultProcessCfg = new HashMap<>();
        defaultProcessCfg.put("x", "y");

        List<DependencyVersionsPolicy.Dependency> dependencies = new ArrayList<>();
        dependencies.add(new DependencyVersionsPolicy.Dependency("foo", "v1"));

        AttachmentsRule attachmentsRule = new AttachmentsRule("msg", 12345L);

        StateRule s1 = new StateRule("msg1", 123L, 456, Collections.singletonList("a"));
        StateRule s2 = new StateRule("msg1", 321L, null, null);
        PolicyRules<StateRule> stateRules = new PolicyRules<>(null, Collections.singletonList(s1), Collections.singletonList(s2));

        List<DependencyRewriteRule> depRewriteRules = Collections.singletonList(new DependencyRewriteRule("mmmsg", "grp", "artifact", "versionFrom", "versionTo", new URI("value")));
        RuntimeRule runtimeRule = new RuntimeRule(null, Collections.singleton("concord-v2"));

        PolicyEngineRules r = new PolicyEngineRules(dependencyRules, fileRules, taskRules, workspaceRule,
                containerRules, queueRules, protectedTasksRules, entityRules, processCfg, new JsonStoreRule(storageRule, storageDataRule),
                defaultProcessCfg, dependencies, attachmentsRule, stateRules, depRewriteRules, runtimeRule);

        r.addCustomRule("ansible", Collections.singletonMap("k", "v"));

        String s = om.writeValueAsString(r);
        System.out.println(">>>" + s);
        PolicyEngineRules rr = om.readValue(s, PolicyEngineRules.class);
        assertEquals(r, rr);
    }
}
