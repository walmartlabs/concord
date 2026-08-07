package com.walmartlabs.concord.runtime.v25.runner.task;

/*-
 * *****
 * Concord
 * ----
 * Copyright (C) 2017 - 2026 Walmart Inc.
 * ----
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

import com.walmartlabs.concord.plugins.mock.MockDefinitionProvider;
import com.walmartlabs.concord.plugins.mock.MockTaskMethodResolver;
import com.walmartlabs.concord.plugins.mock.MockTaskProvider;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.CustomTaskMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.runtime.v25.model.parser.DefinitionParser;
import com.walmartlabs.concord.runtime.v25.runner.EngineFixture;
import com.walmartlabs.concord.runtime.v25.runner.engine.ProcessStatus;
import com.walmartlabs.concord.runtime.v25.runner.engine.RetryScheduler;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import com.walmartlabs.concord.runtime.v25.runner.plan.PlanCompiler;
import com.walmartlabs.concord.runtime.v25.runner.persistence.CheckpointStore;
import com.walmartlabs.concord.runtime.v25.runner.persistence.State25;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MockPluginTest {

    @Test
    void matchesMethodMocksUsingTheExpressionStepMetadata() throws Exception {
        var calls = new AtomicInteger();
        var runtime = mockRuntime(new MethodProvider(calls));

        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    mocks:
                      - task: method
                        method: value
                        stepMeta:
                          target: expression-method
                        result: mocked
                flows:
                  default:
                    - expr: ${method.value()}
                      meta:
                        target: expression-method
                      out: result
                """, runtime);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals("mocked", result.variables().get("result"));
        assertEquals(0, calls.get(), "the real method must not run when its metadata mock matches");
    }

    @Test
    void executesMockNestedFlowsFromExpressionMethods() throws Exception {
        var calls = new AtomicInteger();
        var runtime = mockRuntime(new MethodProvider(calls));

        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    mocks:
                      - task: method
                        method: flow
                        args: [input-value]
                        executeFlow: mocked-flow
                flows:
                  default:
                    - expr: ${method.flow('input-value')}
                      out: result
                  mocked-flow:
                    - set:
                        result:
                          value: ${args[0]}
                """, runtime);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(Map.of("value", "input-value"), result.variables().get("result"));
        assertEquals(0, calls.get(), "the real method must not run when its flow mock matches");
    }

    @Test
    void suspendsAndResumesWhenAnExpressionMethodRequestsSuspension() throws Exception {
        var runtime = runtime(new SuspensionProvider());
        var expressions = new ExpressionService(runtime);
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - expr: ${suspender.waitFor('expression-event')}
                      out: ignored
                    - set:
                        afterResume: true
                """.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var state = new AtomicReference<State25>();
        var checkpoints = new CheckpointStore() {
            @Override
            public void save(String name, State25 value) {
                state.set(value);
            }

            @Override
            public State25 load() {
                return state.get();
            }
        };
        var engine = EngineFixture.engine(expressions, 256, runtime, 64, RetryScheduler.SYSTEM,
                Duration.ofSeconds(5), checkpoints);

        var suspended = engine.run(plan, "default", Map.of(), result -> { });

        assertEquals(ProcessStatus.SUSPENDED, suspended.status(), String.valueOf(suspended.failure()));
        assertNotNull(suspended.suspension());
        assertEquals("expression-event", suspended.suspension().eventName());
        assertEquals("flows.default[0]", suspended.suspension().path());
        assertNull(suspended.failure());
        assertNotNull(state.get());

        var resumed = engine.resume(plan, state.get(), "expression-event", Map.of(), result -> { });

        assertEquals(ProcessStatus.SUCCEEDED, resumed.status(), String.valueOf(resumed.failure()));
        assertEquals(true, resumed.variables().get("afterResume"));
    }


    @Test
    void resumesSetStepsWhoseExpressionMethodSuspends() throws Exception {
        var runtime = runtime(new SuspensionProvider());
        var expressions = new ExpressionService(runtime);
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - set:
                        answer: ${suspender.waitFor('set-event')}
                """.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var state = new AtomicReference<State25>();
        var checkpoints = new CheckpointStore() {
            @Override
            public void save(String name, State25 value) {
                state.set(value);
            }

            @Override
            public State25 load() {
                return state.get();
            }
        };
        var engine = EngineFixture.engine(expressions, 256, runtime, 64, RetryScheduler.SYSTEM,
                Duration.ofSeconds(5), checkpoints);

        var suspended = engine.run(plan, "default", Map.of(), result -> { });
        var resumed = engine.resume(plan, state.get(), "set-event", Map.of("answer", "approved"),
                result -> { });

        assertEquals(ProcessStatus.SUSPENDED, suspended.status());
        assertEquals(ProcessStatus.SUCCEEDED, resumed.status(), String.valueOf(resumed.failure()));
        assertEquals("approved", resumed.variables().get("answer"));
    }
    private static com.walmartlabs.concord.runtime.v25.runner.engine.ProcessResult run(String source,
                                                                                         TaskRuntime runtime)
            throws Exception {
        var expressions = new ExpressionService(runtime);
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream(
                source.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        return EngineFixture.engine(expressions, runtime).run(plan, "default", Map.of(), result -> { });
    }

    private static TaskRuntime mockRuntime(TaskProvider original) {
        var mock = new MockTaskProvider(new MockDefinitionProvider(), () -> List.of(original));
        return runtime(List.of(mock, original), List.of(new MockTaskMethodResolver()));
    }

    private static TaskRuntime runtime(TaskProvider provider) {
        return runtime(List.of(provider), List.of());
    }

    private static TaskRuntime runtime(List<? extends TaskProvider> providers,
                                       List<CustomTaskMethodResolver> methodResolvers) {
        var environment = new TaskEnvironment(null, Path.of("target/mock-plugin-test"), false, false, Map.of(),
                null, null, null, null, null, null, methodResolvers, List.of(), Map.of());
        return new TaskRuntime(new TaskRegistry(providers), environment);
    }

    private static final class MethodProvider implements TaskProvider {

        private final AtomicInteger calls;

        private MethodProvider(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public Task createTask(Context context, String key) {
            return "method".equals(key) ? new MethodTask(calls) : null;
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return "method".equals(key) ? MethodTask.class : null;
        }

        @Override
        public boolean hasTask(String key) {
            return "method".equals(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("method");
        }
    }

    public static final class MethodTask implements Task {

        private final AtomicInteger calls;

        private MethodTask(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success();
        }

        public String value() {
            calls.incrementAndGet();
            return "real";
        }

        public Map<String, Object> flow(String value) {
            calls.incrementAndGet();
            return Map.of("value", value);
        }
    }

    private static final class SuspensionProvider implements TaskProvider {

        @Override
        public Task createTask(Context context, String key) {
            return "suspender".equals(key) ? new SuspensionTask(context) : null;
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return "suspender".equals(key) ? SuspensionTask.class : null;
        }

        @Override
        public boolean hasTask(String key) {
            return "suspender".equals(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("suspender");
        }
    }

    public static final class SuspensionTask implements Task {

        private final Context context;

        private SuspensionTask(Context context) {
            this.context = context;
        }

        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success();
        }

        public String waitFor(String eventName) {
            context.suspend(eventName);
            return "queued";
        }
    }
}
