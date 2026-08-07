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

import com.walmartlabs.concord.forms.FormField;
import com.walmartlabs.concord.runtime.common.FormService;
import com.walmartlabs.concord.runtime.v25.model.parser.DefinitionParser;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import com.walmartlabs.concord.runtime.v25.runner.EngineFixture;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskEnvironment;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRegistry;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime;
import com.walmartlabs.concord.runtime.v25.runner.persistence.FileCheckpointStore;
import com.walmartlabs.concord.runtime.v25.runner.persistence.State25;
import com.walmartlabs.concord.runtime.v25.runner.plan.PlanCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void staticFormSurvivesRestartAndPublishesSubmission() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                forms:
                  approval:
                    - comment:
                        type: string?
                        label: "${label}"
                        value: "${initial}"
                flows:
                  default:
                    - form: approval
                    - set:
                        completed: ${comment}
                """;
        var first = runner(source, "static");
        var events = new ArrayList<LifecycleEvent>();

        var suspended = first.engine().run(first.plan(), "default",
                Map.of("label", "Review", "initial", "pending"), recording(events));

        assertEquals(ProcessStatus.SUSPENDED, suspended.status());
        var form = first.forms().list().getFirst();
        assertEquals("approval", form.name());
        assertEquals(suspended.suspension().eventName(), form.eventName());
        assertEquals("comment", form.fields().getFirst().name());
        assertEquals("Review", form.fields().getFirst().label());
        assertEquals("pending", form.fields().getFirst().defaultValue());
        assertEquals(FormField.Cardinality.ONE_OR_NONE, form.fields().getFirst().cardinality());

        var second = runner(source, "static");
        var completed = second.engine().resume(second.plan(), first.store().load(), form.eventName(),
                Map.of("comment", "approved"), recording(events));
        assertEquals(ProcessStatus.SUCCEEDED, completed.status());
        assertEquals("approved", completed.variables().get("completed"));
        assertEquals(List.of(LifecycleEvent.Type.STEP_STARTED, LifecycleEvent.Type.SUSPENDED,
                        LifecycleEvent.Type.RESUMED, LifecycleEvent.Type.STEP_COMPLETED,
                        LifecycleEvent.Type.STEP_STARTED, LifecycleEvent.Type.STEP_COMPLETED),
                events.stream().map(LifecycleEvent::type).toList());
        assertEquals(events.get(1).correlationId(), events.get(2).correlationId());
        assertEquals(form.eventName(), events.get(2).eventName());
        assertEquals("concord.yml", events.get(2).source());
    }

    @Test
    void checkpointEmitsStableSourceDiagnostic() throws Exception {
        var runner = runner("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - checkpoint: beforeDeploy
                    - set:
                        completed: true
                """, "checkpoint");
        var events = new ArrayList<LifecycleEvent>();

        var completed = runner.engine().run(runner.plan(), "default", Map.of(), recording(events));

        assertEquals(ProcessStatus.SUCCEEDED, completed.status());
        assertEquals(List.of(LifecycleEvent.Type.STEP_STARTED, LifecycleEvent.Type.CHECKPOINT_SAVED,
                        LifecycleEvent.Type.STEP_STARTED, LifecycleEvent.Type.STEP_COMPLETED),
                events.stream().map(LifecycleEvent::type).toList());
        assertEquals("beforeDeploy", events.get(1).data().get("checkpointName"));
        assertEquals("concord.yml", events.get(1).source());
        assertEquals("flows.default[0]", events.getFirst().path());
    }

    @Test
    void restartPublishesNamedCheckpointAsResumeEvent() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - checkpoint: beforeDeploy
                    - expr: "${hasVariable('resumeEvents') ? resumeEvents : []}"
                      out: observed
                """;
        var first = runner(source, "resume-events");
        var initial = first.engine().run(first.plan(), "default", Map.of(), ignored -> {
        });

        assertEquals(ProcessStatus.SUCCEEDED, initial.status(), String.valueOf(initial.failure()));
        var second = runner(source, "resume-events-restarted");
        var restarted = second.engine().restart(second.plan(), first.store().load(), ignored -> {
        });

        assertEquals(ProcessStatus.SUCCEEDED, restarted.status(), String.valueOf(restarted.failure()));
        assertEquals(List.of("beforeDeploy"), restarted.variables().get("observed"));
    }

    @Test
    void rejectsDuplicateLiveFormNamesInParallelBranches() throws Exception {
        var runner = runner("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - parallel:
                        - form: approval
                          fields: [{comment: string}]
                        - form: approval
                          fields: [{comment: string}]
                """, "duplicate-parallel-form");

        var result = runner.engine().run(runner.plan(), "default", Map.of(), ignored -> {
        });

        assertEquals(ProcessStatus.FAILED, result.status());
        assertTrue(result.failure().message().contains("Form 'approval'"));
        assertTrue(result.failure().message().contains("multiple live parallel branches"));
    }

    @Test
    void restoresParallelLoopCheckpointsUsingTheStrippedInstruction() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - try:
                        - checkpoint: item-safe-point
                      loop:
                        items: [one, two]
                        mode: parallel
                      error:
                        - throw: loop error handler must not be restored
                """;
        var first = runner(source, "parallel-loop-checkpoint");
        var initial = first.engine().run(first.plan(), "default", Map.of(), ignored -> {
        });
        var state = first.store().load();
        var parallel = state.root().continuation().stream()
                .filter(frame -> frame instanceof State25.StepState)
                .map(frame -> (State25.StepState) frame)
                .map(State25.StepState::parallel)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();

        assertEquals(ProcessStatus.SUCCEEDED, initial.status());
        assertTrue(parallel.children().stream()
                .flatMap(child -> child.instructionIds().stream())
                .allMatch(id -> id < 0));

        var second = runner(source, "parallel-loop-checkpoint-restored");
        var restarted = second.engine().restart(second.plan(), state, ignored -> {
        });

        assertEquals(ProcessStatus.SUCCEEDED, restarted.status());
    }

    @Test
    void parallelFailureHandlersCanReachDurableSafePoints() throws Exception {
        for (var handler : List.of("suspend", "form", "checkpoint")) {
            assertParallelFailureHandlerSafePoint(handler, false);
            assertParallelFailureHandlerSafePoint(handler, true);
        }
    }

    @Test
    void dynamicFormEvaluatesFieldsValuesAndOptionsOnce() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - form: dynamic
                      fields: "${dynamicFields}"
                      values:
                        age: "${age}"
                      runAs:
                        username: "${owner}"
                      yield: true
                """;
        var fields = List.of(Map.of("age", Map.of(
                "type", "int+",
                "label", "Age",
                "min", 18)));
        var runner = runner(source, "dynamic");

        var suspended = runner.engine().run(runner.plan(), "default", Map.of(
                "dynamicFields", fields,
                "age", List.of(21, 22),
                "owner", "alice"), ignored -> {
                });

        assertEquals(ProcessStatus.SUSPENDED, suspended.status());
        var form = runner.forms().list().getFirst();
        assertEquals("dynamic", form.name());
        assertEquals(List.of(21, 22), form.fields().getFirst().defaultValue());
        assertEquals(FormField.Cardinality.AT_LEAST_ONE, form.fields().getFirst().cardinality());
        assertEquals(18, form.fields().getFirst().options().get("min"));
        assertEquals(true, form.options().isYield());
        assertEquals("alice", form.options().runAs().get("username"));
    }

    private static StatusCallback recording(List<LifecycleEvent> events) {
        return new StatusCallback() {
            @Override
            public void onEvent(LifecycleEvent event) {
                events.add(event);
            }

            @Override
            public void onTerminal(ProcessResult result) {
            }
        };
    }

    private Runner runner(String source, String name) throws Exception {
        var expressions = new ExpressionService();
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream(
                source.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var store = new FileCheckpointStore(temporaryDirectory.resolve(name + "-state.bin"));
        var forms = new FormService(temporaryDirectory.resolve(name + "-forms"));
        var runtime = new TaskRuntime(new TaskRegistry(List.of()), TaskEnvironment.local(temporaryDirectory));
        var engine = EngineFixture.engine(expressions, 256, runtime, 4, RetryScheduler.SYSTEM,
                Duration.ofSeconds(1), store, forms);
        return new Runner(engine, plan, store, forms);
    }

    private void assertParallelFailureHandlerSafePoint(String handler, boolean loop) throws Exception {
        var source = (loop
                ? """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - try:
                        - throw: boom
                      loop:
                        items: [one, two]
                        mode: parallel
                      error:
                        - %s
                    - set:
                        completed: true
                """
                : """
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - try:
                        - parallel:
                            - throw: boom
                            - set:
                                sibling: true
                      error:
                        - %s
                    - set:
                        completed: true
                """).formatted(handlerStep(handler));
        var suffix = handler + (loop ? "-loop" : "-block");
        var expressions = new ExpressionService();
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream(
                source.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var store = new FileCheckpointStore(temporaryDirectory.resolve(suffix + "-state.bin"));
        var forms = new FormService(temporaryDirectory.resolve(suffix + "-forms"));
        var runtime = new TaskRuntime(new TaskRegistry(List.of()), TaskEnvironment.local(temporaryDirectory));
        var engine = EngineFixture.engine(expressions, 256, runtime, 4, RetryScheduler.SYSTEM,
                Duration.ofSeconds(1), store, forms);

        var initial = engine.run(plan, "default", Map.of(), ignored -> {
        });
        ProcessResult completed;
        if ("checkpoint".equals(handler)) {
            completed = engine.restart(plan, store.load(), ignored -> {
            });
        } else if ("form".equals(handler)) {
            var eventName = forms.list().getFirst().eventName();
            completed = engine.resume(plan, store.load(), eventName, Map.of(), ignored -> {
            });
        } else {
            completed = engine.resume(plan, store.load(), "safe", Map.of(), ignored -> {
            });
        }

        assertEquals("checkpoint".equals(handler) ? ProcessStatus.SUCCEEDED : ProcessStatus.SUSPENDED,
                initial.status());
        assertEquals(ProcessStatus.SUCCEEDED, completed.status(), suffix);
        assertEquals(true, completed.variables().get("completed"));
    }

    private static String handlerStep(String handler) {
        return switch (handler) {
            case "suspend" -> "suspend: safe";
            case "form" -> "form: handled\n          fields: [{comment: string}]";
            case "checkpoint" -> "checkpoint: safe";
            default -> throw new IllegalArgumentException(handler);
        };
    }

    private record Runner(Engine engine,
                          com.walmartlabs.concord.runtime.v25.runner.plan.ExecutionPlan plan,
                          FileCheckpointStore store, FormService forms) {
    }
}
