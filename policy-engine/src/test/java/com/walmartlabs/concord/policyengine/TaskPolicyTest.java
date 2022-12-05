package com.walmartlabs.concord.policyengine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaskPolicyTest {

    @Test
    public void testDenyByTaskName() {
        TaskRule r = TaskRule.builder()
                .taskName("taskName-.*")
                .build();

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-1234", "foo");
    }

    @Test
    public void testAllowByTaskName() {
        TaskRule allowRule = TaskRule.builder()
                .taskName("taskName-1234")
                .build();

        TaskRule denyRule = TaskRule.builder()
                .taskName(".*")
                .build();

        PolicyRules<TaskRule> rules = new PolicyRules<>(Collections.singletonList(allowRule), null, Collections.singletonList(denyRule));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertAllow(policy, "taskName-1234", "foo");
    }

    @Test
    public void testDenyByMethodName() {
        TaskRule r = TaskRule.builder()
                .taskName("taskName-.*")
                .method("foo")
                .build();

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-1234", "foo");
    }

    @Test
    public void testAllowByMethodName() {
        TaskRule allowRule = TaskRule.builder()
                .taskName("taskName-1234")
                .method("foo")
                .build();

        TaskRule denyRule = TaskRule.builder()
                .taskName(".*")
                .method(".*")
                .build();

        PolicyRules<TaskRule> rules = new PolicyRules<>(Collections.singletonList(allowRule), null, Collections.singletonList(denyRule));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertAllow(policy, "taskName-1234", "foo");
    }

    @Test
    public void testDenyByStringParams() {
        TaskRule.Param p1 = TaskRule.Param.builder()
                        .index(1)
                        .protectedVariable(false)
                        .addValues("value-1")
                        .build();

        TaskRule.Param p2 = TaskRule.Param.builder()
                .index(0)
                .protectedVariable(false)
                .addValues("value-2")
                .build();

        TaskRule r = TaskRule.builder()
                .taskName("taskName-.*")
                .method("foo")
                .addParams(p1, p2)
                .build();

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-12", "foo", "value-2", "value-1");
    }

    @Test
    public void testDenyByMapStringParam() {
        TaskRule.Param p1 = TaskRule.Param.builder()
                .index(1)
                .name("k")
                .protectedVariable(false)
                .addValues("v")
                .build();

        TaskRule r = TaskRule.builder()
                .taskName("taskName-.*")
                .method("foo")
                .addParams(p1)
                .build();

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-12", "foo", "xxx", Collections.singletonMap("k", "v"));
    }

    @Test
    public void testDenyByMapMapStringParam() {
        TaskRule.Param p1 = TaskRule.Param.builder()
                .index(1)
                .name("k.kk")
                .protectedVariable(false)
                .addValues("v")
                .build();

        TaskRule r = TaskRule.builder()
                .taskName("taskName-.*")
                .method("foo")
                .addParams(p1)
                .build();

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-12", "foo", "xxx", Collections.singletonMap("k", Collections.singletonMap("kk", "v")));
    }

    @Test
    public void testDenyByMapMapStringParamNull() {
        TaskRule.Param p1 = TaskRule.Param.builder()
                .index(1)
                .name("k.kk")
                .protectedVariable(false)
                .values(Collections.singletonList(null))
                .build();

        TaskRule r = TaskRule.builder()
                .taskName("taskName-.*")
                .method("foo")
                .addParams(p1)
                .build();

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-12", "foo", "xxx", Collections.singletonMap("k1", "v"));
    }

    @Test
    public void testDenyByTaskResultsSimpleObject() {
        TaskRule.TaskResult tr1 = TaskRule.TaskResult.builder()
                .task("taskName-12")
                .addValues("result1")
                .build();

        TaskRule r = TaskRule.builder()
                .taskName("taskName-.*")
                .method("foo")
                .addTaskResults(tr1)
                .build();

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDenyByTaskResults(policy, "taskName-12", "foo", Collections.singletonMap("taskName-12", Collections.singletonList("result1")));
    }

    @Test
    public void testDenyByTaskResultsMap() {
        TaskRule.TaskResult tr1 = TaskRule.TaskResult.builder()
                .task("taskName-12")
                .result("k.k1")
                .addValues("result1")
                .build();

        TaskRule r = TaskRule.builder()
                .taskName("taskName-.*")
                .method("foo")
                .addTaskResults(tr1)
                .build();

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        List<Serializable> taskResults = new ArrayList<>();
        taskResults.add("result12");
        taskResults.add(singletonMap("k", singletonMap("k1", "result1")));

        assertDenyByTaskResults(policy, "taskName-12", "foo", Collections.singletonMap("taskName-12", taskResults));
    }

    private static void assertDeny(TaskPolicy policy, String taskName, String methodName, Object... params) {
        CheckResult<TaskRule, String> result = policy.check(taskName, methodName, params, null);
        assertFalse(result.getDeny().isEmpty());
    }

    private static void assertAllow(TaskPolicy policy, String taskName, String methodName, Object... params) {
        CheckResult<TaskRule, String> result = policy.check(taskName, methodName, params, null);
        assertTrue(result.getDeny().isEmpty());
    }

    private static void assertDenyByTaskResults(TaskPolicy policy, String taskName, String methodName, Map<String, List<Serializable>> taskResults) {
        CheckResult<TaskRule, String> result = policy.check(taskName, methodName, null, taskResults);
        assertFalse(result.getDeny().isEmpty());
    }

    private static HashMap<String, Serializable> singletonMap(String k, Serializable v) {
        HashMap<String, Serializable> result = new HashMap<>();
        result.put(k, v);
        return result;
    }
}
