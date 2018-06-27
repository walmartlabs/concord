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


import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaskPolicyTest {

    @Test
    public void testDenyByTaskName() {
        TaskRule r = new TaskRule(null, "taskName-.*", null, null);

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-1234", "foo");
    }

    @Test
    public void testAllowByTaskName() {
        TaskRule allowRule = new TaskRule(null, "taskName-1234", null, null);
        TaskRule denyRule = new TaskRule(null, ".*", null, null);

        PolicyRules<TaskRule> rules = new PolicyRules<>(Collections.singletonList(allowRule), null, Collections.singletonList(denyRule));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertAllow(policy, "taskName-1234", "foo");
    }

    @Test
    public void testDenyByMethodName() {
        TaskRule r = new TaskRule(null, "taskName-.*", "foo", null);

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-1234", "foo");
    }

    @Test
    public void testAllowByMethodName() {
        TaskRule allowRule = new TaskRule(null, "taskName-1234", "foo", null);
        TaskRule denyRule = new TaskRule(null, ".*", ".*", null);

        PolicyRules<TaskRule> rules = new PolicyRules<>(Collections.singletonList(allowRule), null, Collections.singletonList(denyRule));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertAllow(policy, "taskName-1234", "foo");
    }

    @Test
    public void testDenyByStringParams() {
        TaskRule.Param p1 = new TaskRule.Param(1, null, Collections.singletonList("value-1"));
        TaskRule.Param p2 = new TaskRule.Param(0, null, Collections.singletonList("value-2"));

        TaskRule r = new TaskRule(null, "taskName-.*", "foo", Arrays.asList(p1, p2));

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-12", "foo", "value-2", "value-1");
    }

    @Test
    public void testDenyByMapStringParam() {
        TaskRule.Param p1 = new TaskRule.Param(1, "k", Collections.singletonList("v"));

        TaskRule r = new TaskRule(null, "taskName-.*", "foo", Collections.singletonList(p1));

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-12", "foo", "xxx", Collections.singletonMap("k", "v"));
    }

    @Test
    public void testDenyByMapMapStringParam() {
        TaskRule.Param p1 = new TaskRule.Param(1, "k.kk", Collections.singletonList("v"));

        TaskRule r = new TaskRule(null, "taskName-.*", "foo", Collections.singletonList(p1));

        PolicyRules<TaskRule> rules = new PolicyRules<>(null, null, Collections.singletonList(r));

        TaskPolicy policy = new TaskPolicy(rules);

        // ---
        assertDeny(policy, "taskName-12", "foo", "xxx", Collections.singletonMap("k", Collections.singletonMap("kk", "v")));
    }

    private static void assertDeny(TaskPolicy policy, String taskName, String methodName, Object...params) {
        CheckResult<TaskRule, String> result = policy.check(taskName, methodName, params);
        assertFalse(result.getDeny().isEmpty());
    }

    private static void assertAllow(TaskPolicy policy, String taskName, String methodName, Object...params) {
        CheckResult<TaskRule, String> result = policy.check(taskName, methodName, params);
        assertTrue(result.getDeny().isEmpty());
    }
}
