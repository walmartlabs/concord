package com.walmartlabs.concord.runtime.v25.runner.task;

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

import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.PolicyEngineRules;
import com.walmartlabs.concord.policyengine.PolicyRules;
import com.walmartlabs.concord.policyengine.TaskRule;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPolicyHookTest {

    @Test
    void rejectsDeniedTaskCallsAndAllowsOtherTasks() {
        var denied = TaskRule.builder().taskName("blocked").method("execute").build();
        var rules = PolicyEngineRules.builder()
                .taskRules(new PolicyRules<>(null, null, List.of(denied)))
                .build();
        var hook = new TaskPolicyHook(new PolicyEngine(rules));

        var error = assertThrows(UserDefinedException.class,
                () -> hook.before(invocation("blocked", "execute")));
        assertTrue(error.getMessage().contains("blocked.execute"), error::getMessage);
        assertDoesNotThrow(() -> hook.before(invocation("allowed", "execute")));
    }

    private static TaskRuntime.Invocation invocation(String taskName, String methodName) {
        return new TaskRuntime.Invocation(taskName, methodName, List.of(Map.of("value", 1)), List.of(), null);
    }
}
