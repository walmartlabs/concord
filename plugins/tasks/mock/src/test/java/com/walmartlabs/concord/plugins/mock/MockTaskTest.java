package com.walmartlabs.concord.plugins.mock;

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

import com.walmartlabs.concord.runtime.model.Location;
import com.walmartlabs.concord.runtime.v2.Constants;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.model.TaskCallOptions;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.DryRunReady;
import com.walmartlabs.concord.runtime.v2.sdk.Execution;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.NestedFlowExecutor;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MockTaskTest {

    @Test
    void executesMockFlowThroughSdkExecutor() throws Exception {
        var receivedFlow = new AtomicReference<String>();
        var receivedInput = new AtomicReference<Map<String, Object>>();
        NestedFlowExecutor nestedFlowExecutor = (flowName, input) -> {
            receivedFlow.set(flowName);
            receivedInput.set(input);
            return (java.io.Serializable) Map.of("fromNestedFlow", "value");
        };
        var mock = Map.<String, Object>of("task", "target", "executeFlow", "nested");
        var context = context(Map.of("mocks", java.util.List.of(mock)), nestedFlowExecutor);
        var task = new MockTask(context, "target", new MockDefinitionProvider(), Task.class,
                () -> {
                    throw new AssertionError("the real task must not run");
                });

        var result = (TaskResult.SimpleResult) task.execute(new MapBackedVariables(Map.of("value", "input")));

        assertEquals("nested", receivedFlow.get());
        assertEquals(Map.of("value", "input"), receivedInput.get());
        assertEquals(Map.of("fromNestedFlow", "value"), result.values());
    }

    @Test
    void rejectsNonDryRunReadyDelegateFallback() {
        var invoked = new AtomicBoolean();
        var context = context(Map.of("mocks", java.util.List.of(Map.of("task", "target", "in", Map.of("expected", true)))),
                null, true);
        var task = new MockTask(context, "target", new MockDefinitionProvider(), NonDryRunReadyTask.class,
                () -> new RecordingTask(invoked));

        var error = org.junit.jupiter.api.Assertions.assertThrows(UserDefinedException.class,
                () -> task.execute(new MapBackedVariables(Map.of("actual", true))));

        assertEquals("Dry-run mode is not supported for 'target' task", error.getMessage());
        org.junit.jupiter.api.Assertions.assertFalse(invoked.get());
    }

    @Test
    void executesDryRunReadyDelegateFallback() throws Exception {
        var invoked = new AtomicBoolean();
        var context = context(Map.of("mocks", java.util.List.of(Map.of("task", "target", "in", Map.of("expected", true)))),
                null, true);
        var task = new MockTask(context, "target", new MockDefinitionProvider(), DryRunReadyTask.class,
                () -> new RecordingTask(invoked));

        task.execute(new MapBackedVariables(Map.of("actual", true)));

        org.junit.jupiter.api.Assertions.assertTrue(invoked.get());
    }

    @Test
    void executesStepDryRunReadyDelegateFallback() throws Exception {
        var invoked = new AtomicBoolean();
        var currentStep = new TaskCall(Location.builder().build(), "target",
                TaskCallOptions.builder().meta(Map.of("dryRunReady", true)).build());
        var context = context(Map.of("mocks", java.util.List.of(Map.of("task", "target", "in", Map.of("expected", true)))),
                null, true, currentStep);
        var task = new MockTask(context, "target", new MockDefinitionProvider(), NonDryRunReadyTask.class,
                () -> new RecordingTask(invoked));

        task.execute(new MapBackedVariables(Map.of("actual", true)));

        org.junit.jupiter.api.Assertions.assertTrue(invoked.get());
    }

    @Test
    void matchesStepNameFromContext() {
        var currentStep = new TaskCall(Location.builder().build(), "target",
                TaskCallOptions.builder().meta(Map.of(Constants.SEGMENT_NAME, "named-step")).build());
        var context = context(Map.of("mocks", java.util.List.of(Map.of("task", "target", "stepName", "named-step"))),
                null, false, currentStep);

        var mock = new MockDefinitionProvider().find(context, "target", new MapBackedVariables(Map.of()));

        assertNotNull(mock);
    }

    private static final class NonDryRunReadyTask implements Task {

        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success();
        }
    }

    @DryRunReady
    private static final class DryRunReadyTask implements Task {

        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success();
        }
    }

    private static final class RecordingTask implements Task {

        private final AtomicBoolean invoked;

        private RecordingTask(AtomicBoolean invoked) {
            this.invoked = invoked;
        }

        @Override
        public TaskResult execute(Variables input) {
            invoked.set(true);
            return TaskResult.success();
        }
    }

    private static Context context(Map<String, Object> variables, NestedFlowExecutor nestedFlowExecutor) {
        return context(variables, nestedFlowExecutor, false);
    }

    private static Context context(Map<String, Object> variables, NestedFlowExecutor nestedFlowExecutor, boolean dryRun) {
        return context(variables, nestedFlowExecutor, dryRun, null);
    }

    private static Context context(Map<String, Object> variables, NestedFlowExecutor nestedFlowExecutor, boolean dryRun,
                                   Step currentStep) {
        var execution = (Execution) Proxy.newProxyInstance(Execution.class.getClassLoader(),
                new Class<?>[]{Execution.class}, (proxy, method, args) ->
                        "currentStep".equals(method.getName()) ? currentStep : null);
        var processConfiguration = (ProcessConfiguration) Proxy.newProxyInstance(ProcessConfiguration.class.getClassLoader(),
                new Class<?>[]{ProcessConfiguration.class}, (proxy, method, args) ->
                        "dryRun".equals(method.getName()) ? dryRun : false);
        return (Context) Proxy.newProxyInstance(Context.class.getClassLoader(), new Class<?>[]{Context.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "variables" -> new MapBackedVariables(variables);
                    case "execution" -> execution;
                    case "processConfiguration" -> processConfiguration;
                    case "nestedFlowExecutor" -> nestedFlowExecutor;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
