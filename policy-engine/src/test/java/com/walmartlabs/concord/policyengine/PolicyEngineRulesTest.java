package com.walmartlabs.concord.policyengine;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class PolicyEngineRulesTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    public void serializationTest() throws Exception {
        DependencyRule r1 = DependencyRule.builder()
                .msg("msg1")
                .scheme("schema1")
                .groupId("groupId1")
                .artifactId("artifactId1")
                .fromVersion("fromVersion1")
                .toVersion("toVersion1")
                .build();

        DependencyRule r2 = DependencyRule.builder()
                .msg("msg2")
                .scheme("schema2")
                .groupId("groupId2")
                .artifactId("artifactId2")
                .fromVersion("fromVersion2")
                .toVersion("toVersion2")
                .build();

        DependencyRule r3 = DependencyRule.builder()
                .msg("msg3")
                .scheme("schema3")
                .groupId("groupId3")
                .artifactId("artifactId3")
                .fromVersion("fromVersion3")
                .toVersion("toVersion3")
                .build();
        PolicyRules<DependencyRule> dependencyRules = new PolicyRules<>(Collections.singletonList(r1), Collections.singletonList(r2), Collections.singletonList(r3));

        FileRule f1 = new FileRule("msg1", "1K", "FILE", Collections.singletonList("name1"));
        FileRule f2 = new FileRule("msg2", "2K", "DIR", Collections.singletonList("name2"));
        FileRule f3 = new FileRule("msg3", "3K", "FILE", Collections.singletonList("name3"));
        PolicyRules<FileRule> fileRules = new PolicyRules<>(Collections.singletonList(f1), Collections.singletonList(f2), Collections.singletonList(f3));

        TaskRule t1 = TaskRule.builder()
                .msg("msg1")
                .taskName("taskName1")
                .method("methodName1")
                .addParams()
                .build()
                .withParams(
                        TaskRule.Param.builder()
                                .index(1)
                                .name("name1")
                                .protectedVariable(true)
                                .values(Collections.singletonList("values"))
                                .build())
                .withTaskResults(
                        TaskRule.TaskResult.builder()
                                .task("task1")
                                .result("result1")
                                .build());

        TaskRule t2 = TaskRule.builder()
                .msg("msg2")
                .taskName("taskName2")
                .method("methodName2")
                .addParams()
                .build()
                .withParams(
                        TaskRule.Param.builder()
                                .index(2)
                                .name("name2")
                                .protectedVariable(true)
                                .values(Collections.singletonList("values"))
                                .build());

        TaskRule t3 = TaskRule.builder()
                .msg("msg3")
                .taskName("taskName3")
                .method("methodName3")
                .addParams()
                .build()
                .withParams(
                        TaskRule.Param.builder()
                                .index(3)
                                .name("name3")
                                .protectedVariable(true)
                                .values(Collections.singletonList("values"))
                                .build());

        PolicyRules<TaskRule> taskRules = new PolicyRules<>(Collections.singletonList(t1), Collections.singletonList(t2), Collections.singletonList(t3));

        WorkspaceRule workspaceRule = WorkspaceRule.of("msg", 12345L, Collections.singleton("a"));

        ContainerRule containerRules = ContainerRule.of("msg1", "maxRam", 2);

        ConcurrentProcessRule concurrent = ConcurrentProcessRule.builder()
                .msg("nsg1")
                .maxPerProject(23)
                .maxPerOrg(433)
                .build();
        ForkDepthRule forkDepthRule = ForkDepthRule.of("msg1", 12);
        ProcessTimeoutRule processTimeoutRule = ProcessTimeoutRule.of("msg1", "13");
        QueueRule queueRules = QueueRule.of(concurrent, forkDepthRule, processTimeoutRule);

        ProtectedTasksRule protectedTasksRules = ProtectedTasksRule.of(Collections.singleton("task1"));

        EntityRule e1 = EntityRule.builder()
                .msg("msg1")
                .entity("entity1")
                .action("action1")
                .conditions(Collections.singletonMap("k1", "v"))
                .build();
        EntityRule e2 = EntityRule.builder()
                .msg("msg2")
                .entity("entity2")
                .action("action2")
                .conditions(Collections.singletonMap("k2", "v"))
                .build();
        EntityRule e3 = EntityRule.builder()
                .msg("msg3")
                .entity("entity3")
                .action("action3")
                .conditions(Collections.singletonMap("k3", "v"))
                .build();
        PolicyRules<EntityRule> entityRules = new PolicyRules<>(Collections.singletonList(e1), Collections.singletonList(e2), Collections.singletonList(e3));

        Map<String, Object> processCfg = new HashMap<>();
        processCfg.put("a", "b");

        JsonStoreRule.StoreRule storageRule = JsonStoreRule.StoreRule.of("storage-rule", 123);
        JsonStoreRule.StoreDataRule storageDataRule = JsonStoreRule.StoreDataRule.of("storage-data-rule", 11L);

        Map<String, Object> defaultProcessCfg = new HashMap<>();
        defaultProcessCfg.put("x", "y");

        List<DependencyVersionsPolicy.Dependency> dependencies = new ArrayList<>();
        dependencies.add(new DependencyVersionsPolicy.Dependency("foo", "v1"));

        AttachmentsRule attachmentsRule = AttachmentsRule.of("msg", 12345L);

        RawPayloadRule rawPayloadRule = RawPayloadRule.of("msg", 12345L);
        
        StateRule s1 = StateRule.builder()
                .msg("msg1")
                .maxSizeInBytes(123L)
                .maxFilesCount(456)
                .patterns(Collections.singletonList("a"))
                .build();
        StateRule s2 = StateRule.builder()
                .maxSizeInBytes(321L)
                .build();
        PolicyRules<StateRule> stateRules = new PolicyRules<>(null, Collections.singletonList(s1), Collections.singletonList(s2));

        List<DependencyRewriteRule> depRewriteRules = Collections.singletonList(
                DependencyRewriteRule.builder()
                        .msg("msg")
                        .groupId("group")
                        .artifactId("artifact")
                        .fromVersion("fromVersion")
                        .toVersion("versionTo")
                        .value(new URI("value"))
                        .build());
        RuntimeRule runtimeRule = RuntimeRule.of(null, Collections.singleton("concord-v2"), null);

        PolicyEngineRules r = PolicyEngineRules.builder()
                .dependencyRules(dependencyRules)
                .dependencyRewriteRules(depRewriteRules)
                .fileRules(fileRules)
                .taskRules(taskRules)
                .workspaceRule(workspaceRule)
                .containerRules(containerRules)
                .queueRules(queueRules)
                .protectedTasksRules(protectedTasksRules)
                .entityRules(entityRules)
                .processCfg(processCfg)
                .jsonStoreRule(JsonStoreRule.of(storageRule, storageDataRule))
                .defaultProcessCfg(defaultProcessCfg)
                .dependencyVersions(dependencies)
                .attachmentsRule(attachmentsRule)
                .stateRules(stateRules)
                .rawPayloadRule(rawPayloadRule)
                .runtimeRule(runtimeRule)
                .putCustomRule("ansible", Collections.singletonMap("k", "v"))
                .cronTriggerRule(CronTriggerRule.of("msg", 1000))
                .kvRule(KvRule.of("msg", 123))
                .build();

        String s = om.writeValueAsString(r);
        PolicyEngineRules rr = om.readValue(s, PolicyEngineRules.class);
        assertEquals(r, rr);
    }

    @Test
    public void deserializeTest1() throws Exception {
        PolicyEngineRules r = om.readValue(resource("policy1.json"), PolicyEngineRules.class);

        assertNotNull(r.queueRules());
        assertEquals(5, r.queueRules().forkDepthRule().max());
        assertEquals(Collections.singletonMap("__currentFlow", "n/a"), r.processCfg().get("meta"));
        assertEquals(Collections.singleton("gatekeeper"), r.protectedTasksRules().names());
        assertEquals(2, r.dependencyRewriteRules().size());

        DependencyRewriteRule rw = r.dependencyRewriteRules().get(0);
        assertEquals("msg1", rw.msg());
        assertEquals("mvn://com.walmartlabs.concord.plugins.basic:ansible-tasks:1.71.0", rw.value().toString());
        assertEquals("com.walmartlabs.concord.plugins.basic", rw.groupId());
        assertEquals("ansible-tasks", rw.artifactId());
        assertEquals("1.62.0", rw.fromVersion());
        assertEquals("1.70.3", rw.toVersion());

        rw = r.dependencyRewriteRules().get(1);
        assertEquals("msg2", rw.msg());
        assertEquals("mvn://com.walmartlabs.concord.plugins:git:1.33.0", rw.value().toString());
        assertEquals("com.walmartlabs.concord.plugins", rw.groupId());
        assertEquals("git", rw.artifactId());
        assertEquals("1.25.0", rw.fromVersion());
        assertEquals("1.32.3", rw.toVersion());
    }

    @Test
    public void deserializeTest2() throws Exception {
        PolicyEngineRules r = om.readValue(resource("policy2.json"), PolicyEngineRules.class);

        assertEquals("PT2H", r.queueRules().processTimeoutRule().max());
        assertEquals("PT1H", r.processCfg().get("processTimeout"));
        assertEquals(Collections.singletonMap("agent", Collections.singletonMap("flavor", "xxx-tunr")), r.processCfg().get("requirements"));
    }

    @Test
    public void deserializeTest3() throws Exception {
        PolicyEngineRules r = om.readValue(resource("policy3.json"), PolicyEngineRules.class);

        assertEquals(20, r.queueRules().concurrentRule().maxPerProject());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void deserializeTest4() throws Exception {
        PolicyEngineRules r = om.readValue(resource("policy4.json"), PolicyEngineRules.class);

        assertEquals(1, r.taskRules().getDeny().size());

        TaskRule tr = r.taskRules().getDeny().get(0);
        assertEquals("msg1", tr.msg());
        assertEquals("execute", tr.method());
        assertEquals("ansible.*", tr.taskName());
        assertTrue(tr.taskResults().isEmpty());
        assertEquals(1, tr.params().size());

        TaskRule.Param p = tr.params().get(0);
        assertEquals("gatekeeperResult", p.name());
        assertEquals(0, p.index());
        assertTrue(p.protectedVariable());
        assertEquals(2, p.values().size());
        assertEquals(Arrays.asList(false, null), p.values());

        assertEquals(0, r.taskRules().getAllow().size());
        assertEquals(0, r.taskRules().getWarn().size());

        assertEquals(Collections.singleton("gatekeeper"), r.protectedTasksRules().names());

        assertNotNull(r.customRule().get("ansible"));

        Map<String, Object> ansible = (Map<String, Object>) r.customRule().get("ansible");
        assertEquals(3, ansible.size());
    }

    @Test
    public void deserializeTest5() throws Exception {
        PolicyEngineRules r = om.readValue(resource("policy5.json"), PolicyEngineRules.class);

        assertEquals(4, r.entityRules().getDeny().size());

        EntityRule er = r.entityRules().getDeny().get(0);
        assertEquals("New project creation is disabled in this organization by the environment's policy", er.msg());
        assertEquals("create", er.action());
        assertEquals("project", er.entity());
        assertEquals(1, er.conditions().size());
        assertEquals(Collections.singletonMap("orgId", ".*"), er.conditions().get("entity"));

        er = r.entityRules().getDeny().get(1);
        assertEquals("Subscribing to all GitHub repository notifications is not allowed", er.msg());
        assertEquals("create", er.action());
        assertEquals("trigger", er.entity());
        assertEquals(1, er.conditions().size());
        Map<String, Object> params = new HashMap<>();
        params.put("org", "\\.\\*");
        params.put("project", "\\.\\*");
        params.put("repository", "\\.\\*");
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("params", params);
        conditions.put("eventSource", "github");
        assertEquals(conditions, er.conditions().get("entity"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void deserializeTest6() throws Exception {
        PolicyEngineRules r = om.readValue(resource("policy6.json"), PolicyEngineRules.class);

        assertEquals(2, ((Map)r.defaultProcessCfg().get("defaultTaskVariables")).size());
    }

    private static URL resource(String name) {
        URL r = PolicyEngineRulesTest.class.getResource(name);
        assertNotNull(r);
        return r;
    }
}
