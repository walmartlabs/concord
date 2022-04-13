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
        TaskRule r = new TaskRule(null, "taskName-.*", null, null, null);

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-1234", "foo");
    }

    @Test
    public void testAllowByTaskName() {
        TaskRule allowRule = new TaskRule(null, "taskName-1234", null, null, null);
        TaskRule denyRule = new TaskRule(null, ".*", null, null, null);

        PolicyRules<TaskRule> rules = new PolicyRules<>(Collections.singletonList(allowRule), null, Collections.singletonList(denyRule));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertAllow(policy, "taskName-1234", "foo");
    }

    @Test
    public void testDenyByMethodName() {
        TaskRule r = new TaskRule(null, "taskName-.*", "foo", null, null);

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-1234", "foo");
    }

    @Test
    public void testAllowByMethodName() {
        TaskRule allowRule = new TaskRule(null, "taskName-1234", "foo", null, null);
        TaskRule denyRule = new TaskRule(null, ".*", ".*", null, null);

        PolicyRules<TaskRule> rules = new PolicyRules<>(Collections.singletonList(allowRule), null, Collections.singletonList(denyRule));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertAllow(policy, "taskName-1234", "foo");
    }

    @Test
    public void testDenyByStringParams() {
        TaskRule.Param p1 = new TaskRule.Param(1, null, false, Collections.singletonList("value-1"));
        TaskRule.Param p2 = new TaskRule.Param(0, null, false, Collections.singletonList("value-2"));

        TaskRule r = new TaskRule(null, "taskName-.*", "foo", Arrays.asList(p1, p2), null);

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-12", "foo", "value-2", "value-1");
    }

    @Test
    public void testDenyByMapStringParam() {
        TaskRule.Param p1 = new TaskRule.Param(1, "k", false, Collections.singletonList("v"));

        TaskRule r = new TaskRule(null, "taskName-.*", "foo", Collections.singletonList(p1), null);

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-12", "foo", "xxx", Collections.singletonMap("k", "v"));
    }

    @Test
    public void testDenyByMapMapStringParam() {
        TaskRule.Param p1 = new TaskRule.Param(1, "k.kk", false, Collections.singletonList("v"));

        TaskRule r = new TaskRule(null, "taskName-.*", "foo", Collections.singletonList(p1), null);

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-12", "foo", "xxx", Collections.singletonMap("k", Collections.singletonMap("kk", "v")));
    }

    @Test
    public void testDenyByMapMapStringParamNull() {
        TaskRule.Param p1 = new TaskRule.Param(1, "k.kk", false, Collections.singletonList(null));

        TaskRule r = new TaskRule(null, "taskName-.*", "foo", Collections.singletonList(p1), null);

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-12", "foo", "xxx", Collections.singletonMap("k1", "v"));
    }

    @Test
    public void testDenyByTaskResultsSimpleObject() {
        TaskRule.TaskResult tr1 = new TaskRule.TaskResult("taskName-12", null, Collections.singletonList("result1"));

        TaskRule r = new TaskRule(null, "taskName-.*", "foo", null, Collections.singletonList(tr1));

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDenyByTaskResults(policy, "taskName-12", "foo", Collections.singletonMap("taskName-12", Collections.singletonList("result1")));
    }

    @Test
    public void testDenyByTaskResultsMap() {
        TaskRule.TaskResult tr1 = new TaskRule.TaskResult("taskName-12", "k.k1", Collections.singletonList("result1"));

        TaskRule r = new TaskRule(null, "taskName-.*", "foo", null, Collections.singletonList(tr1));

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
