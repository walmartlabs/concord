package com.walmartlabs.concord.runtime.v25.runner.engine;

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

import com.walmartlabs.concord.runtime.v25.model.parser.DefinitionParser;
import com.walmartlabs.concord.runtime.v25.runner.EngineFixture;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import com.walmartlabs.concord.runtime.v25.runner.plan.PlanCompiler;
import com.walmartlabs.concord.runtime.v25.runner.persistence.CheckpointStore;
import com.walmartlabs.concord.runtime.v25.runner.persistence.State25;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskEnvironment;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRegistry;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTest {

    @Test
    void executesCallsAndPublishesOnlyDeclaredOutputs() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    seed: 2
                    nullable: null
                  out:
                    - result
                    - nullable
                    - continued
                    - nested.value
                    - values.1
                flows:
                  default:
                    - call: calculate
                      in:
                        n: ${seed}
                      out:
                        result: ${value}
                    - set:
                        continued: true
                    - set:
                        nested.value: remote
                    - set:
                        values: [zero, one]
                  calculate:
                    - expr: ${n * 3}
                      out: value
                    - set:
                        internal: secret
                    - if: true
                      then:
                        - return
                    - set:
                        value: 0
                """);

        assertEquals(ProcessStatus.SUCCEEDED, result.status());
        assertEquals(6L, result.outputs().get("result"));
        assertTrue(result.outputs().containsKey("nullable"));
        assertNull(result.outputs().get("nullable"));
        assertEquals(true, result.outputs().get("continued"));
        assertEquals("remote", result.outputs().get("nested.value"));
        assertEquals("one", result.outputs().get("values.1"));
        assertFalse(result.variables().containsKey("internal"));
        assertNull(result.failure());
    }

    @Test
    void isolatesGroupScopeAndPublishesNull() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    nullable: null
                  out: [exported, hiddenVisible]
                flows:
                  default:
                    - block:
                        - set:
                            local: ${nullable}
                            hidden: secret
                      out:
                        exported: ${local}
                    - expr: ${hasVariable('hidden')}
                      out: hiddenVisible
                """);

        assertEquals(ProcessStatus.SUCCEEDED, result.status());
        assertTrue(result.outputs().containsKey("exported"));
        assertNull(result.outputs().get("exported"));
        assertEquals(false, result.outputs().get("hiddenVisible"));
        assertFalse(result.variables().containsKey("hidden"));
    }

    @Test
    void executesIfSwitchAndExitWithoutRunningLaterSteps() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    enabled: true
                  out: [branch, selected, afterExit]
                flows:
                  default:
                    - if: ${enabled}
                      then:
                        - set:
                            branch: then
                      else:
                        - set:
                            branch: else
                    - switch: ${branch}
                      then:
                        - set:
                            selected: "yes"
                      default:
                        - set:
                            selected: "no"
                    - exit
                    - set:
                        afterExit: true
                """);

        assertEquals(ProcessStatus.SUCCEEDED, result.status());
        assertEquals("then", result.outputs().get("branch"));
        assertEquals("yes", result.outputs().get("selected"));
        assertFalse(result.outputs().containsKey("afterExit"));
    }

    @Test
    void reportsSourceLocatedFailureAndInvokesCallbackOnce() throws Exception {
        var expressions = new ExpressionService();
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - expr: ${throw('boom')}
                """.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var callbacks = new ArrayList<ProcessResult>();

        var result = EngineFixture.engine(expressions).run(plan, "default", Map.of(), callbacks::add);

        assertEquals(ProcessStatus.FAILED, result.status());
        assertEquals(1, callbacks.size());
        assertEquals(result, callbacks.getFirst());
        assertEquals("V25_STEP_FAILED", result.failure().code());
        assertEquals("boom", result.failure().message());
        assertEquals("concord.yml", result.failure().source());
        assertEquals("flows.default[0]", result.failure().path());
        assertTrue(result.failure().line() > 0);
        assertTrue(result.failure().column() > 0);
    }

    @Test
    void emitsOrderedV2CompatibleStepMetadata() throws Exception {
        var expressions = new ExpressionService();
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - expr: 1
                      out: first
                    - expr: 2
                      out: second
                """.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var events = new ArrayList<LifecycleEvent>();

        var result = EngineFixture.engine(expressions).run(plan, "default", Map.of(), new StatusCallback() {
            @Override
            public void onEvent(LifecycleEvent event) {
                events.add(event);
            }

            @Override
            public void onTerminal(ProcessResult ignored) {
            }
        });

        assertEquals(ProcessStatus.SUCCEEDED, result.status());
        assertEquals(List.of(LifecycleEvent.Type.STEP_STARTED, LifecycleEvent.Type.STEP_COMPLETED,
                        LifecycleEvent.Type.STEP_STARTED, LifecycleEvent.Type.STEP_COMPLETED),
                events.stream().map(LifecycleEvent::type).toList());
        assertEquals(List.of("flows.default[0]", "flows.default[0]", "flows.default[1]", "flows.default[1]"),
                events.stream().map(LifecycleEvent::path).toList());
        assertEquals("default", events.getFirst().data().get("processDefinitionId"));
        assertEquals("concord.yml", events.getFirst().data().get("fileName"));
        assertEquals("expr", events.getFirst().data().get("description"));
        assertEquals(events.getFirst().correlationId(), events.getFirst().data().get("correlationId"));
        assertEquals(events.getFirst().instructionId(), events.getFirst().data().get("instructionId"));
    }

    @Test
    void supportsDynamicCallsAndStringAndListOutputs() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    target: child
                    untouched: root
                  out: [first, second, untouched]
                flows:
                  default:
                    - call: child
                      out: first
                    - call: ${target}
                      out: [second]
                  child:
                    - set:
                        first: 1
                        second: 2
                        untouched: child
                """);

        assertEquals(ProcessStatus.SUCCEEDED, result.status());
        assertEquals(1, result.outputs().get("first"));
        assertEquals(2, result.outputs().get("second"));
        assertEquals("root", result.outputs().get("untouched"));
    }

    @Test
    void enforcesFlowRecursionLimit() throws Exception {
        var expressions = new ExpressionService();
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - call: recursive
                  recursive:
                    - call: recursive
                """.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);

        var result = EngineFixture.engine(expressions, 1).run(plan, "default", Map.of(), ignored -> {
        });

        assertEquals(ProcessStatus.FAILED, result.status());
        assertTrue(result.failure().message().contains("Maximum flow call depth"));
        assertEquals("flows.recursive[0]", result.failure().path());
    }

    @Test
    void carriesCallDepthAcrossParallelBranches() throws Exception {
        var expressions = new ExpressionService();
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - call: recursive
                  recursive:
                    - parallel:
                        - call: recursive
                """.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);

        var result = EngineFixture.engine(expressions, 2).run(plan, "default", Map.of(), ignored -> {
        });

        assertEquals(ProcessStatus.FAILED, result.status());
        assertTrue(result.failure().message().contains("Maximum flow call depth of 2 exceeded"));
    }


    @Test
    void rejectsCheckpointNamesEvaluatingToTheReservedSuspendSentinel() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    checkpointName: suspend
                flows:
                  default:
                    - checkpoint: ${checkpointName}
                """);

        assertEquals(ProcessStatus.FAILED, result.status());
        assertTrue(result.failure().message().contains("checkpoint name 'suspend' is reserved"));
    }

    @Test
    void publishesNamedValuesFromListOutputs() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - expr: {first: one, second: two}
                      out: [first, second, absent]
                """);

        assertEquals(ProcessStatus.SUCCEEDED, result.status());
        assertEquals("one", result.variables().get("first"));
        assertEquals("two", result.variables().get("second"));
        assertFalse(result.variables().containsKey("absent"));
    }

    @Test
    void rejectsRetryDelaysThatCannotConvertToMilliseconds() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: missing
                      retry:
                        times: 0
                        delay: 9223372036854776
                """);

        assertEquals(ProcessStatus.FAILED, result.status());
        assertTrue(result.failure().message().contains("retry.delay"));
        assertTrue(result.failure().message().contains(Long.toString(Long.MAX_VALUE / 1_000L)));
    }
    @Test
    void evaluatesSetEntriesAgainstEarlierStagedValues() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - set:
                        obj.name: Concord
                        obj.msg: "Hello, ${obj.name}!"
                        sibling: new
                        copiedSibling: ${sibling}
                """);

        assertEquals(ProcessStatus.SUCCEEDED, result.status());
        assertEquals(Map.of("name", "Concord", "msg", "Hello, Concord!"), result.variables().get("obj"));
        assertEquals("new", result.variables().get("copiedSibling"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void interruptionStopsPureInterpreterLoopsWithoutFurtherSideEffects() throws Exception {
        var expressions = new ExpressionService();
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - block:
                        - expr: ${counter.incrementAndGet()}
                      loop:
                        items: ${items}
                """.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var invocations = new AtomicInteger();
        var result = new AtomicReference<ProcessResult>();
        var runner = Thread.ofPlatform().start(() -> result.set(EngineFixture.engine(expressions)
                .run(plan, "default", Map.of("items", Collections.nCopies(2_000_000, 0),
                        "counter", invocations), ignored -> {
                        })));

        try {
            awaitInvocations(invocations);
        } finally {
            runner.interrupt();
            runner.join(5_000);
        }
        var observed = invocations.get();
        Thread.sleep(50);

        assertFalse(runner.isAlive());
        assertEquals(ProcessStatus.CANCELLED, result.get().status());
        assertEquals(observed, invocations.get());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void handledParallelFailuresDoNotLeakIntoLaterCancellation() throws Exception {
        var expressions = new ExpressionService();
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - try:
                        - parallel:
                            - throw: handled failure
                            - set:
                                sibling: true
                      error:
                        - set:
                            handled: true
                    - suspend: continue
                    - block:
                        - expr: ${counter.incrementAndGet()}
                      loop:
                        items: ${items}
                """.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var store = new MemoryCheckpointStore();
        var runtime = new TaskRuntime(new TaskRegistry(List.of()), TaskEnvironment.local(
                Path.of("target", "engine-regression-test")));
        var engine = EngineFixture.engine(expressions, 256, runtime, 4, RetryScheduler.SYSTEM,
                java.time.Duration.ofSeconds(1), store);
        var invocations = new AtomicInteger();
        var initial = engine.run(plan, "default", Map.of("items", Collections.nCopies(2_000_000, 0),
                "counter", invocations), ignored -> {
        });
        var resumed = new AtomicReference<ProcessResult>();
        var runner = Thread.ofPlatform().start(() -> resumed.set(engine.resume(plan, store.state, "continue",
                Map.of(), ignored -> {
                })));

        try {
            assertEquals(ProcessStatus.SUSPENDED, initial.status());
            awaitInvocations(invocations);
        } finally {
            runner.interrupt();
            runner.join(5_000);
        }

        assertFalse(runner.isAlive());
        assertEquals(ProcessStatus.CANCELLED, resumed.get().status());
        assertNull(resumed.get().failure());
    }

    @Test
    void doesNotPublishPartialOutputMappings() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - block:
                        - set:
                            local: complete
                      out:
                        exported: ${local}
                        broken: ${throw('mapping failed')}
                """);

        assertEquals(ProcessStatus.FAILED, result.status());
        assertEquals("mapping failed", result.failure().message());
        assertFalse(result.variables().containsKey("exported"));
        assertFalse(result.variables().containsKey("broken"));
    }

    private ProcessResult run(String source) throws Exception {
        var expressions = new ExpressionService();
        var definition = new DefinitionParser().parse("concord.yml",
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        return EngineFixture.engine(expressions).run(plan, "default", Map.of(), ignored -> {
        });
    }

    private static final class MemoryCheckpointStore implements CheckpointStore {

        private State25 state;

        @Override
        public void save(String name, State25 state) {
            this.state = state;
        }

        @Override
        public State25 load() {
            return state;
        }
    }

    private static void awaitInvocations(AtomicInteger invocations) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (invocations.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertTrue(invocations.get() > 0);
    }
}
