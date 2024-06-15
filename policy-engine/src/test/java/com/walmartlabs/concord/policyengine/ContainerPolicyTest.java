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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContainerPolicyTest {

    @Test
    public void testCpu() {
        ContainerPolicy oneCpu = new ContainerPolicy(ContainerRule.of("1 CPU", null, 1));
        ContainerPolicy twoCpu = new ContainerPolicy(ContainerRule.of("1 CPU", null, 2));

        Map<String, Object> containerParams = new HashMap<>();
        containerParams.put("cpu", 2);

        // ---

        assertDeny(oneCpu, containerParams);
        assertAllow(twoCpu, containerParams);
    }

    @Test
    public void testRam() {
        ContainerPolicy ram1 = new ContainerPolicy(ContainerRule.of("128 RAM", "128m", null));
        ContainerPolicy ram2 = new ContainerPolicy(ContainerRule.of("256 RAM", "256m", null));

        Map<String, Object> containerParams = new HashMap<>();
        containerParams.put("ram", "256m");

        // ---

        assertDeny(ram1, containerParams);
        assertAllow(ram2, containerParams);
    }

    private static void assertAllow(ContainerPolicy policy, Map<String, Object> p) {
        CheckResult<ContainerRule, Object> result = policy.check(p);
        assertTrue(result.getDeny().isEmpty());
    }

    private static void assertDeny(ContainerPolicy policy, Map<String, Object> p) {
        CheckResult<ContainerRule, Object> result = policy.check(p);
        assertFalse(result.getDeny().isEmpty());
    }
}
