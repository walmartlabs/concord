package com.walmartlabs.concord.runtime.v25.runner.persistence;

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

import com.walmartlabs.concord.runtime.common.SensitiveDataMasker;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.ReentrantTask;
import com.walmartlabs.concord.runtime.v2.sdk.ResumeEvent;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.runtime.v25.model.parser.DefinitionParser;
import com.walmartlabs.concord.runtime.v25.runner.engine.Engine;
import com.walmartlabs.concord.runtime.v25.runner.EngineFixture;
import com.walmartlabs.concord.runtime.v25.runner.engine.ProcessResult;
import com.walmartlabs.concord.runtime.v25.runner.engine.ProcessStatus;
import com.walmartlabs.concord.runtime.v25.runner.engine.RetryScheduler;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import com.walmartlabs.concord.runtime.v25.runner.plan.ExecutionPlan;
import com.walmartlabs.concord.runtime.v25.runner.plan.PlanCompiler;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskEnvironment;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRegistry;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void checkpointRestartsAtTheNextInstruction() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - set:
                        before: 1
                    - checkpoint: stable
                      meta:
                        reason: test
                    - set:
                        after: ${before + 1}
                """;
        var store = new FileCheckpointStore(temporaryDirectory.resolve("state.bin"));
        var first = runner(source, null, store);

        var completed = first.engine().run(first.plan(), "default", Map.of(), ignored -> {
        });
        assertEquals(ProcessStatus.SUCCEEDED, completed.status());
        assertEquals(2L, completed.variables().get("after"));
        var state = store.load();
        assertNotNull(state);
        assertEquals("stable", state.checkpointName());
        assertEquals("test", state.checkpointMetadata().get("reason"));

        var second = runner(source, null, store);
        var restarted = second.engine().restart(second.plan(), state, ignored -> {
        });
        assertEquals(ProcessStatus.SUCCEEDED, restarted.status());
        assertEquals(1, restarted.variables().get("before"));
        assertEquals(2L, restarted.variables().get("after"));
    }

    @Test
    void suspensionSurvivesAFreshEngineAndConsumesOnlyTheMatchingEvent() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - set:
                        before: 1
                    - suspend: approval
                    - set:
                        after: ${answer}
                """;
        var statePath = temporaryDirectory.resolve("state.bin");
        var store = new FileCheckpointStore(statePath);
        var first = runner(source, null, store);
        var suspended = first.engine().run(first.plan(), "default", Map.of(), ignored -> {
        });

        assertEquals(ProcessStatus.SUSPENDED, suspended.status());
        var state = store.load();
        var bytes = Files.readAllBytes(statePath);
        var second = runner(source, null, store);
        assertThrows(IllegalArgumentException.class, () -> second.engine().resume(second.plan(), state,
                "unrelated", Map.of("answer", 41), ignored -> {
                }));
        assertArrayEquals(bytes, Files.readAllBytes(statePath));

        var resumed = second.engine().resume(second.plan(), state, Set.of("approval"),
                Map.of("answer", 41), ignored -> {
                });
        assertEquals(ProcessStatus.SUCCEEDED, resumed.status());
        assertEquals(41, resumed.variables().get("after"));
    }

    @Test
    void parallelSuspensionsResumeOutOfOrderAcrossFreshEngines() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - parallel:
                        - block:
                            - suspend: first-event
                            - set:
                                firstDone: ${firstAnswer}
                          out: firstDone
                        - block:
                            - suspend: second-event
                            - set:
                                secondDone: ${secondAnswer}
                          out: secondDone
                      out: [firstDone, secondDone]
                    - set:
                        after: true
                """;
        var store = new FileCheckpointStore(temporaryDirectory.resolve("parallel-state.bin"));
        var first = runner(source, null, store);
        var suspended = first.engine().run(first.plan(), "default", Map.of(), ignored -> {
        });
        assertEquals(ProcessStatus.SUSPENDED, suspended.status());

        var initialState = store.load();
        var all = runner(source, null, store);
        var allResumed = all.engine().resume(all.plan(), initialState,
                Set.of("first-event", "second-event"),
                Map.of("firstAnswer", "first", "secondAnswer", "second"), ignored -> {
                });
        assertEquals(ProcessStatus.SUCCEEDED, allResumed.status());
        assertEquals("first", allResumed.variables().get("firstDone"));
        assertEquals("second", allResumed.variables().get("secondDone"));

        var second = runner(source, null, store);
        var stillSuspended = second.engine().resume(second.plan(), store.load(), "second-event",
                Map.of("secondAnswer", "second"), ignored -> {
                });
        assertEquals(ProcessStatus.SUSPENDED, stillSuspended.status());
        assertEquals("first-event", stillSuspended.suspension().eventName());

        var third = runner(source, null, store);
        var completed = third.engine().resume(third.plan(), store.load(), "first-event",
                Map.of("firstAnswer", "first"), ignored -> {
                });
        assertEquals(ProcessStatus.SUCCEEDED, completed.status());
        assertEquals("first", completed.variables().get("firstDone"));
        assertEquals("second", completed.variables().get("secondDone"));
        assertEquals(true, completed.variables().get("after"));
    }

    @Test
    void duplicateParallelSuspensionEventsFailWithAnActionableDiagnostic() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - parallel:
                        - suspend: duplicate
                        - suspend: duplicate
                """;
        var runner = runner(source, null,
                new FileCheckpointStore(temporaryDirectory.resolve("duplicate-state.bin")));

        var result = runner.engine().run(runner.plan(), "default", Map.of(), ignored -> {
        });

        assertEquals(ProcessStatus.FAILED, result.status());
        assertEquals("DUPLICATE_SUSPENSION_EVENT", result.failure().code());
        assertTrue(result.failure().message().contains("duplicate"));
        assertTrue(result.failure().message().contains(" and "));
    }

    @Test
    void reentrantTaskIsResolvedAgainAndReceivesItsPersistedState() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: reentrant
                      out: result
                    - set:
                        done: ${result.resumed}
                """;
        var provider = new ReentrantProvider();
        var store = new FileCheckpointStore(temporaryDirectory.resolve("state.bin"));
        var first = runner(source, provider, store);
        var suspended = first.engine().run(first.plan(), "default", Map.of(), ignored -> {
        });
        assertEquals(ProcessStatus.SUSPENDED, suspended.status());

        var second = runner(source, provider, store);
        var resumed = second.engine().resume(second.plan(), store.load(), "reentrant-event",
                Map.of("submission", true), ignored -> {
                });
        assertEquals(ProcessStatus.SUCCEEDED, resumed.status());
        assertEquals("persisted", resumed.variables().get("done"));
        assertEquals(true, resumed.variables().get("submission"));
        assertEquals(2, provider.created.get());
    }

    @Test
    void checkpointInsideParallelPersistsAQuiescentBarrier() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - parallel:
                        - block:
                            - task: count
                              out: invocation
                            - checkpoint: parallel-safe
                            - set:
                                left: ${invocation.value}
                          out: left
                        - set:
                            right: stable
                      out: [left, right]
                    - set:
                        after: true
                """;
        var provider = new CountingProvider();
        var store = new FileCheckpointStore(temporaryDirectory.resolve("barrier-state.bin"));
        var first = runner(source, provider, store);
        assertEquals(0, provider.invocations.get());

        var completed = first.engine().run(first.plan(), "default", Map.of(), ignored -> {
        });
        assertEquals(ProcessStatus.SUCCEEDED, completed.status());
        assertEquals(1, provider.invocations.get());
        var checkpoint = store.load();
        assertEquals("parallel-safe", checkpoint.checkpointName());

        var second = runner(source, provider, store);
        var restarted = second.engine().restart(second.plan(), checkpoint, ignored -> {
        });
        assertEquals(ProcessStatus.SUCCEEDED, restarted.status());
        assertEquals(1, restarted.variables().get("left"));
        assertEquals("stable", restarted.variables().get("right"));
        assertEquals(true, restarted.variables().get("after"));
        assertEquals(1, provider.invocations.get());
    }

    @Test
    void codecPreservesNullCyclesAndRejectsForeignFormats() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - suspend: event
                """;
        var cycle = new LinkedHashMap<String, Object>();
        cycle.put("nullable", null);
        cycle.put("self", cycle);
        var store = new FileCheckpointStore(temporaryDirectory.resolve("state.bin"));
        var runner = runner(source, null, store);
        runner.engine().run(runner.plan(), "default", Map.of("cycle", cycle), ignored -> {
        });

        var restored = store.load();
        var restoredCycle = (Map<?, ?>) restored.root().scopes().getFirst().overlay().get("cycle");
        assertTrue(restoredCycle.containsKey("nullable"));
        assertSame(restoredCycle, restoredCycle.get("self"));

        var codec = new State25Codec();
        var unknown = new ByteArrayOutputStream();
        unknown.write(new byte[]{'C', 'V', '2', '5', 0, 0, 0, 99});
        assertThrows(State25Codec.StateFormatException.class,
                () -> codec.read(new ByteArrayInputStream(unknown.toByteArray())));

        var v2 = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(v2)) {
            output.writeObject("legacy");
        }
        var error = assertThrows(State25Codec.StateFormatException.class,
                () -> codec.read(new ByteArrayInputStream(v2.toByteArray())));
        assertTrue(error.getMessage().contains("runtime-v2"));
    }

    @Test
    void codecRejectsCorruptedBodiesAndReadResolvePoisoning() throws Exception {
        var codec = new State25Codec();
        var bytes = new ByteArrayOutputStream();
        codec.write(bytes, stateWithValue("value"));
        var corrupted = bytes.toByteArray();
        corrupted[corrupted.length - 1] ^= 1;

        var checksumError = assertThrows(State25Codec.StateFormatException.class,
                () -> codec.read(new ByteArrayInputStream(corrupted)));
        assertTrue(checksumError.getMessage().contains("SHA-256"));

        var poisonError = assertThrows(State25Codec.StateFormatException.class, () -> {
            var output = new ByteArrayOutputStream();
            codec.write(output, stateWithValue(new ReadResolvePoison()));
        });
        assertTrue(poisonError.getMessage().contains("persistence verification"));
        assertTrue(poisonError.getMessage().contains("non-durable"));
    }

    @Test
    void codecRoundTripsNestedAndCyclicGraphs() throws Exception {
        var random = new java.util.Random(1);
        for (var i = 0; i < 20; i++) {
            var nested = new LinkedHashMap<String, Object>();
            nested.put("value", random.nextLong());
            nested.put("items", List.of(random.nextInt(), Map.of("nested", random.nextBoolean())));
            var cycle = new LinkedHashMap<String, Object>();
            cycle.put("nested", nested);
            cycle.put("self", cycle);
            var output = new ByteArrayOutputStream();
            var codec = new State25Codec();
            codec.write(output, stateWithValue(cycle));
            var restored = codec.read(new ByteArrayInputStream(output.toByteArray()));
            var restoredCycle = (Map<?, ?>) restored.root().scopes().getFirst().overlay().get("value");
            assertSame(restoredCycle, restoredCycle.get("self"));
            assertEquals(nested, restoredCycle.get("nested"));
        }
    }

    @Test
    void codecNamesFilterLimitsDuringStatePersistence() {
        Object deep = "leaf";
        for (var i = 0; i < 129; i++) {
            deep = List.of(deep);
        }
        var nested = deep;
        var error = assertThrows(State25Codec.StateFormatException.class, () ->
                new State25Codec().write(new ByteArrayOutputStream(), stateWithValue(nested)));
        assertTrue(error.getMessage().contains("persistence verification"));
        assertTrue(error.getMessage().contains("maximum depth"));
    }

    @Test
    void codecRejectsOversizedArraysDuringStatePersistence() {
        var error = assertThrows(State25Codec.StateFormatException.class, () ->
                new State25Codec().write(new ByteArrayOutputStream(), stateWithValue(new byte[1_000_001])));
        assertTrue(error.getMessage().contains("maximum array length"));
    }

    @Test
    void codecRejectsDeniedClassesDuringStatePersistence() {
        var error = assertThrows(State25Codec.StateFormatException.class, () ->
                new State25Codec().write(new ByteArrayOutputStream(),
                        stateWithValue(new javax.naming.CompositeName("denied"))));
        assertTrue(error.getMessage().contains("denied class"));
    }

    @Test
    void validatorRejectsStateBeyondMaximumDepth() {
        Object deep = "leaf";
        for (var i = 0; i < 130; i++) {
            deep = List.of(deep);
        }
        var nested = deep;

        var error = assertThrows(IllegalArgumentException.class, () -> State25Validator.validate(stateWithValue(nested)));

        assertTrue(error.getMessage().contains("maximum depth of 128"));
    }

    @Test
    void missingCheckpointFileLoadsAsNull() throws Exception {
        var store = new FileCheckpointStore(temporaryDirectory.resolve("missing.bin"));

        assertNull(store.load());
        assertNull(store.generation());
    }

    @Test
    void codecUsesTheDependencyClassLoaderForWriteVerificationAndRead() throws Exception {
        var source = temporaryDirectory.resolve("DependencyValue.java");
        Files.writeString(source, """
                import java.io.Serializable;
                public final class DependencyValue implements Serializable {
                    public final String value;
                    public DependencyValue(String value) { this.value = value; }
                }
                """);
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "-d", temporaryDirectory.toString(), source.toString()));
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{temporaryDirectory.toUri().toURL()}, null)) {
            var type = Class.forName("DependencyValue", true, loader);
            var value = type.getConstructor(String.class).newInstance("dependency");
            var store = new FileCheckpointStore(temporaryDirectory.resolve("dependency.bin"), loader);

            store.save("suspend", stateWithValue(value));

            var restored = store.load().root().scopes().getFirst().overlay().get("value");
            assertEquals(type, restored.getClass());
            assertEquals("dependency", type.getField("value").get(restored));
        }
    }

    @Test
    void supportedFrameSnapshotsRoundTrip() throws Exception {
        var scope = new State25.ScopeState(1, null, "default", false, false, Map.of("value", "persisted"));
        var sequence = new State25.SequenceState(List.of(1, 2), 1, 1, null, Map.of("output", true), true, null);
        var step = new State25.StepState(2, 1, "TASK", true,
                new State25.LoopState(List.of("item"), false, 1),
                new State25.RetryState(2, 10L, Map.of("input", "value"), null),
                0, 1, null, null, null, Map.of("result", List.of("value")), null, null);
        var root = new State25.FiberState(1L, null, State25.FiberStatus.WAITING, 1,
                List.of(scope), List.of(sequence, step), List.of());
        var state = new State25(State25.CURRENT_FORMAT, "plan", "default", ProcessStatus.SUSPENDED,
                null, "suspend", Map.of(), 0L, root, List.of(), List.of());
        var store = new FileCheckpointStore(temporaryDirectory.resolve("frames.bin"));

        store.save("suspend", state);

        assertEquals(List.of(sequence, step), store.load().root().continuation());
    }

    @Test
    void rejectsBrokenSerializableValuesAtTheCheckpointBoundary() throws Exception {
        class BrokenSerializable implements Serializable {

            private final Object value = new Object();
        }

        var scope = new State25.ScopeState(1, null, "default", false, false,
                Map.of("broken", new BrokenSerializable()));
        var root = new State25.FiberState(1L, null, State25.FiberStatus.WAITING, 1,
                List.of(scope), List.of(), List.of());
        var state = new State25(State25.CURRENT_FORMAT, "plan", "default", ProcessStatus.SUSPENDED,
                null, "suspend", Map.of(), 0L, root, List.of(), List.of());
        var store = new FileCheckpointStore(temporaryDirectory.resolve("broken-state.bin"));

        var error = assertThrows(State25Codec.StateFormatException.class, () -> store.save("suspend", state));

        assertTrue(error.getMessage().contains(BrokenSerializable.class.getName()));
        assertTrue(error.getMessage().contains("cannot be serialized"));
    }

    @Test
    void serializableTaskOutputSurvivesCheckpointStateRoundTrip() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: durable
                      out: durable
                    - suspend: approval
                """;
        var store = new FileCheckpointStore(temporaryDirectory.resolve("durable-state.bin"));
        var first = runner(source, new DurableProvider(), store);

        assertEquals(ProcessStatus.SUSPENDED, first.engine().run(first.plan(), "default", Map.of(), ignored -> {
        }).status());
        var state = store.load();
        assertEquals(new DurableValue("persisted"),
                ((Map<?, ?>) state.root().scopes().getFirst().overlay().get("durable")).get("value"));

        var second = runner(source, new DurableProvider(), store);
        var resumed = second.engine().resume(second.plan(), state, "approval", Map.of(), ignored -> {
        });
        assertEquals(ProcessStatus.SUCCEEDED, resumed.status());
        assertEquals(new DurableValue("persisted"), ((Map<?, ?>) resumed.variables().get("durable")).get("value"));
    }

    @Test
    void sensitiveValuesDoNotEnterSerializedState() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - expr: ${sensitive('persisted-secret')}
                    - suspend: approval
                """;
        var stateFile = temporaryDirectory.resolve("sensitive-state.bin");
        var store = new FileCheckpointStore(stateFile);
        var sensitiveData = new SensitiveValues();
        var runner = runner(source, null, store, environment(sensitiveData));

        assertEquals(ProcessStatus.SUSPENDED, runner.engine().run(runner.plan(), "default", Map.of(), ignored -> {
        }).status());
        assertEquals(Set.of("persisted-secret"), sensitiveData.get());
        assertFalse(new String(Files.readAllBytes(stateFile), StandardCharsets.ISO_8859_1).contains("persisted-secret"));
    }

    @Test
    void failedReplacementRetainsTheLastValidCheckpoint() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - suspend: event
                """;
        var statePath = temporaryDirectory.resolve("state.bin");
        var store = new FileCheckpointStore(statePath);
        var runner = runner(source, null, store);
        runner.engine().run(runner.plan(), "default", Map.of(), ignored -> {
        });
        var original = Files.readAllBytes(statePath);
        var valid = store.load();
        var invalid = new State25(State25.CURRENT_FORMAT, valid.planId(), valid.entryPoint(), valid.status(),
                valid.terminalIntent(), valid.checkpointName(), Map.of("bad", new Object()),
                valid.createdAtEpochMilli(), valid.root(), valid.waits(), valid.history());

        var error = assertThrows(State25Codec.StateFormatException.class, () -> store.save("bad", invalid));
        assertTrue(error.getMessage().contains("$.checkpointMetadata.bad"));
        assertTrue(error.getMessage().contains(Object.class.getName()));
        assertArrayEquals(original, Files.readAllBytes(statePath));
    }

    private Runner runner(String source, TaskProvider provider, CheckpointStore store) throws Exception {
        return runner(source, provider, store, provider != null ? TaskEnvironment.local(temporaryDirectory) : null);
    }

    private Runner runner(String source, TaskProvider provider, CheckpointStore store, TaskEnvironment environment)
            throws Exception {
        var tasks = new TaskRuntime(new TaskRegistry(provider == null ? List.of() : List.of(provider)),
                environment != null ? environment : TaskEnvironment.local(temporaryDirectory));
        var expressions = new ExpressionService(tasks);
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream(
                source.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var engine = EngineFixture.engine(expressions, 256, tasks, 4, RetryScheduler.SYSTEM,
                Duration.ofSeconds(1), store);
        return new Runner(engine, plan);
    }

    private record Runner(Engine engine, ExecutionPlan plan) {
    }

    private static final class CountingProvider implements TaskProvider {

        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public Task createTask(Context context, String key) {
            return new Task() {
                @Override
                public TaskResult execute(Variables input) {
                    return TaskResult.success().value("value", invocations.incrementAndGet());
                }
            };
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return Task.class;
        }

        @Override
        public boolean hasTask(String key) {
            return "count".equals(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("count");
        }
    }

    private static final class ReentrantProvider implements TaskProvider {

        private final AtomicInteger created = new AtomicInteger();

        @Override
        public Task createTask(Context context, String key) {
            created.incrementAndGet();
            return new ReentrantTask() {
                @Override
                public TaskResult execute(Variables input) {
                    return TaskResult.reentrantSuspend("reentrant-event", Map.of("token", "persisted"));
                }

                @Override
                public TaskResult resume(ResumeEvent event) {
                    return TaskResult.success().value("resumed", event.state().get("token"));
                }
            };
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return ReentrantTask.class;
        }

        @Override
        public boolean hasTask(String key) {
            return "reentrant".equals(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("reentrant");
        }
    }

    private TaskEnvironment environment(SensitiveDataHolder sensitiveData) {
        return new TaskEnvironment(null, temporaryDirectory, false, false, Map.of(),
                null, null, null, null, null, null, List.of(), List.of(),
                Map.of(SensitiveDataHolder.class, sensitiveData));
    }

    private static State25 stateWithValue(Object value) {
        var scope = new State25.ScopeState(1, null, "default", false, false, Map.of("value", value));
        var root = new State25.FiberState(1L, null, State25.FiberStatus.WAITING, 1, List.of(scope), List.of(), List.of());
        return new State25(State25.CURRENT_FORMAT, "plan", "default", ProcessStatus.SUSPENDED,
                null, "suspend", Map.of(), 0L, root, List.of(), List.of());
    }

    private static final class ReadResolvePoison implements Serializable {
        private Object readResolve() {
            return new Object();
        }
    }

    private record DurableValue(String value) implements Serializable {
    }

    private static final class DurableProvider implements TaskProvider {

        @Override
        public Task createTask(Context context, String key) {
            return new Task() {
                @Override
                public TaskResult execute(com.walmartlabs.concord.runtime.v2.sdk.Variables input) {
                    return TaskResult.success().value("value", new DurableValue("persisted"));
                }
            };
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return Task.class;
        }

        @Override
        public boolean hasTask(String key) {
            return "durable".equals(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("durable");
        }
    }

    private static final class SensitiveValues implements SensitiveDataHolder {

        private final Set<String> values = new LinkedHashSet<>();

        @Override
        public Set<String> get() {
            return Set.copyOf(values);
        }

        @Override
        public void add(String sensitiveData) {
            values.add(sensitiveData);
        }

        @Override
        public void addAll(java.util.Collection<String> sensitiveData) {
            values.addAll(sensitiveData);
        }
    }

}
