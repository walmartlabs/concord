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

import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.runtime.v25.model.parser.DefinitionParser;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import com.walmartlabs.concord.runtime.v25.runner.EngineFixture;
import com.walmartlabs.concord.runtime.v25.runner.plan.PlanCompiler;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskEnvironment;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRegistry;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ControlFlowTest {

    @Test
    void handlesFailuresWithScopedLastError() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - try:
                        - throw: original
                      error:
                        - set:
                            handled: ${lastError.message}
                      out: handled
                    - set:
                        continued: true
                """);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals("original", result.variables().get("handled"));
        assertEquals(true, result.variables().get("continued"));
        assertFalse(result.variables().containsKey("lastError"));
    }

    @Test
    void returnFromChildErrorHandlerUnwindsOnlyChildFlow() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - call: child
                    - set:
                        continued: true
                  child:
                    - try:
                        - throw: child-failure
                      error:
                        - return
                    - set:
                        unreachable: true
                """);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(true, result.variables().get("continued"));
        assertFalse(result.variables().containsKey("unreachable"));
    }

    @Test
    void handlerFailureRetainsOriginalFailureAsSuppressedContext() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - try:
                        - throw: original
                      error:
                        - throw: handler
                """);

        assertEquals(ProcessStatus.FAILED, result.status());
        assertEquals("handler", result.failure().message());
        assertNotNull(result.failure().cause());
        assertEquals(1, result.failure().cause().getSuppressed().length);
        assertEquals("original", result.failure().cause().getSuppressed()[0].getMessage());
    }

    @Test
    void suspendAndExitBypassOrdinaryHandlers() throws Exception {
        var suspended = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - try:
                        - suspend: test-event
                      error:
                        - set:
                            incorrectlyHandled: true
                """);
        assertEquals(ProcessStatus.SUSPENDED, suspended.status());
        assertEquals("test-event", suspended.suspension().eventName());
        assertFalse(suspended.variables().containsKey("incorrectlyHandled"));

        var exited = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - try:
                        - exit
                      error:
                        - set:
                            incorrectlyHandled: true
                    - set:
                        unreachable: true
                """);
        assertEquals(ProcessStatus.SUCCEEDED, exited.status());
        assertFalse(exited.variables().containsKey("incorrectlyHandled"));
        assertFalse(exited.variables().containsKey("unreachable"));
    }

    @Test
    void retriesWithDynamicConfigurationAndInputOverrides() throws Exception {
        var provider = new FlakyProvider();
        var delays = new ArrayList<Duration>();
        RetryScheduler scheduler = delay -> {
            delays.add(delay);
            return CompletableFuture.completedFuture(null);
        };
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    retries: 2
                    retryDelay: 7
                flows:
                  default:
                    - task: flaky
                      in:
                        value: initial
                        succeedAt: 3
                      retry:
                        times: ${retries}
                        delay: ${retryDelay}
                        in:
                          value: retry
                      out: taskResult
                """, Map.of(), provider, scheduler);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(3, provider.invocations);
        assertEquals(List.of("initial", "retry", "retry"), provider.inputs);
        assertEquals(List.of(0, 1, 2), provider.retryAttempts);
        assertEquals(List.of(Duration.ofSeconds(7), Duration.ofSeconds(7)), delays);
        assertEquals("retry", ((Map<?, ?>) result.variables().get("taskResult")).get("value"));
    }

    @Test
    void resolvesRetryAndSerialLoopConfigurationOncePerStepLifecycle() throws Exception {
        var provider = new SideEffectConfigurationProvider();
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: flaky
                      in:
                        value: ${item}
                        succeedOnRetry: true
                      retry:
                        times: ${counter.retries()}
                        delay: 0
                      loop:
                        items: ${counter.items()}
                """, Map.of(), provider, delay -> CompletableFuture.completedFuture(null));

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(4, provider.flaky.invocations);
        assertEquals(1, provider.retryConfigurationCalls.get());
        assertEquals(1, provider.loopConfigurationCalls.get());
    }

    @Test
    void retryExhaustionEntersSurroundingHandlerOnce() throws Exception {
        var provider = new FlakyProvider();
        var delays = new ArrayList<Duration>();
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - try:
                        - task: flaky
                          in:
                            value: attempt
                            succeedAt: 0
                          retry:
                            times: 1
                            delay: 0
                      error:
                        - set:
                            handled: ${lastError.message}
                      out: handled
                """, Map.of(), provider, delay -> {
            delays.add(delay);
            return CompletableFuture.completedFuture(null);
        });

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(2, provider.invocations);
        assertEquals(List.of(Duration.ZERO), delays);
        assertEquals("attempt 2 failed", result.variables().get("handled"));
    }

    @Test
    void exposesConcordCallLoopAndRetryContextToHandlers() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - call: child
                      out:
                        - observedStack
                        - observedIndex
                        - observedAttempt
                      loop:
                        items: [one]
                  child:
                    - task: flaky
                      in:
                        value: failing
                      retry:
                        times: 1
                        delay: 0
                      out:
                        observedStack: null
                        observedIndex: null
                        observedAttempt: null
                      error:
                        - set:
                            observedStack: ${lastError.callStack}
                            observedIndex: ${lastError.loopItemIndex}
                            observedAttempt: ${lastError.retryAttempt}
                """, Map.of(), new FlakyProvider(), delay -> CompletableFuture.completedFuture(null));

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(List.of(List.of("default", "child")), result.variables().get("observedStack"));
        assertEquals(List.of(0), result.variables().get("observedIndex"));
        assertEquals(List.of(1), result.variables().get("observedAttempt"));
    }

    @Test
    void loopIterationsOwnIndependentRetryCountersAndTimesZeroRunsOnce() throws Exception {
        var provider = new FlakyProvider();
        var delays = new ArrayList<Duration>();
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: flaky
                      in:
                        value: ${item}
                        succeedOnRetry: true
                      retry:
                        times: 1
                        delay: 0
                      out: taskResult
                      loop:
                        items: [one, two]
                """, Map.of(), provider, delay -> {
            delays.add(delay);
            return CompletableFuture.completedFuture(null);
        });

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(4, provider.invocations);
        assertEquals(List.of(0, 1, 0, 1), provider.retryAttempts);
        assertEquals(List.of(Duration.ZERO, Duration.ZERO), delays);
        var results = (List<?>) result.variables().get("taskResult");
        assertEquals("one", ((Map<?, ?>) results.get(0)).get("value"));
        assertEquals("two", ((Map<?, ?>) results.get(1)).get("value"));

        provider = new FlakyProvider();
        var once = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - try:
                        - task: flaky
                          in:
                            value: once
                          retry:
                            times: 0
                            delay: 0
                      error:
                        - set:
                            handled: true
                      out: handled
                """, Map.of(), provider, delay -> CompletableFuture.completedFuture(null));
        assertEquals(ProcessStatus.SUCCEEDED, once.status(), String.valueOf(once.failure()));
        assertEquals(1, provider.invocations);
    }

    @Test
    void cancellationInterruptsRetryDelayWithoutEnteringErrorHandler() throws Exception {
        var timer = new AtomicReference<CompletableFuture<Void>>();
        var delayStarted = new CountDownLatch(1);
        RetryScheduler scheduler = delay -> {
            var future = new CompletableFuture<Void>();
            timer.set(future);
            delayStarted.countDown();
            return future;
        };
        var result = new AtomicReference<ProcessResult>();
        var failure = new AtomicReference<Throwable>();
        var runner = Thread.ofVirtual().start(() -> {
            try {
                result.set(run("""
                        configuration:
                          runtime: concord-v2.5
                        flows:
                          default:
                            - try:
                                - task: flaky
                                  in:
                                    value: waiting
                                  retry:
                                    times: 3
                                    delay: 60
                              error:
                                - set:
                                    incorrectlyHandled: true
                        """, Map.of(), new FlakyProvider(), scheduler));
            } catch (Throwable e) {
                failure.set(e);
            }
        });

        assertTrue(delayStarted.await(5, TimeUnit.SECONDS));
        runner.interrupt();
        runner.join(5_000);

        assertFalse(runner.isAlive());
        assertNull(failure.get());
        assertNotNull(result.get());
        assertEquals(ProcessStatus.CANCELLED, result.get().status());
        assertFalse(result.get().variables().containsKey("incorrectlyHandled"));
        assertTrue(timer.get().isCancelled());
    }

    @Test
    void cancellationInterruptsRunningTaskWithoutEnteringErrorHandler() throws Exception {
        var provider = new BlockingProvider();
        var result = new AtomicReference<ProcessResult>();
        var failure = new AtomicReference<Throwable>();
        var runner = Thread.ofVirtual().start(() -> {
            try {
                result.set(run("""
                        configuration:
                          runtime: concord-v2.5
                        flows:
                          default:
                            - try:
                                - task: blocking
                              error:
                                - set:
                                    incorrectlyHandled: true
                        """, Map.of(), provider, RetryScheduler.SYSTEM));
            } catch (Throwable e) {
                failure.set(e);
            }
        });

        assertTrue(provider.started.await(5, TimeUnit.SECONDS));
        runner.interrupt();
        assertTrue(provider.interrupted.await(5, TimeUnit.SECONDS));
        runner.join(5_000);

        assertFalse(runner.isAlive());
        assertNull(failure.get());
        assertNotNull(result.get());
        assertEquals(ProcessStatus.CANCELLED, result.get().status());
        assertFalse(result.get().variables().containsKey("incorrectlyHandled"));
    }

    @Test
    void serialLoopsPreserveOrderAndContextForListsArraysAndMappings() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - call: echo
                      in:
                        value: ${item}
                        index: ${itemIndex}
                      out:
                        - value
                        - index
                      loop:
                        items: [a, b]
                        mode: serial
                    - call: echoMap
                      in:
                        mapKey: ${item.key}
                        mapValue: ${item.value}
                      out:
                        - mapKey
                        - mapValue
                      loop:
                        items:
                          first: 1
                          second: 2
                    - call: echoArray
                      in:
                        arrayValue: ${item}
                      out: arrayValue
                      loop:
                        items: ${arrayItems}
                  echo:
                    - set: {noop: true}
                  echoMap:
                    - set: {noop: true}
                  echoArray:
                    - set: {noop: true}
                """, Map.of("arrayItems", new String[]{"x", "y"}), new FlakyProvider(), RetryScheduler.SYSTEM);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(List.of("a", "b"), result.variables().get("value"));
        assertEquals(List.of(0, 1), result.variables().get("index"));
        assertEquals(List.of("first", "second"), result.variables().get("mapKey"));
        assertEquals(List.of(1, 2), result.variables().get("mapValue"));
        assertEquals(List.of("x", "y"), result.variables().get("arrayValue"));
        assertFalse(result.variables().containsKey("item"));
        assertFalse(result.variables().containsKey("itemIndex"));
    }

    @Test
    void failedLoopDoesNotPublishPartialOutputs() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - call: risky
                      in:
                        value: ${item}
                      out: produced
                      loop:
                        items: [1, 2, 3]
                  risky:
                    - if: ${value == 2}
                      then:
                        - throw: loop-failure
                    - set:
                        produced: ${value}
                """);

        assertEquals(ProcessStatus.FAILED, result.status());
        assertEquals("loop-failure", result.failure().message());
        assertEquals(List.of("default", "risky"), result.failure().callStack());
        assertEquals(1, result.failure().loopItemIndex());
        assertFalse(result.variables().containsKey("produced"));
    }

    @Test
    void emptyLoopPublishesEmptyDeclaredOutputs() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - call: empty
                      out:
                        - one
                        - two
                      loop:
                        items: []
                  empty:
                    - set: {noop: true}
                """);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(List.of(), result.variables().get("one"));
        assertEquals(List.of(), result.variables().get("two"));
    }

    @Test
    void throwMappingPreservesSerializablePayloadForHandler() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - try:
                        - throw:
                            message: BOOM
                            payload:
                              key: value
                      error:
                        - set:
                            payloadValue: ${lastError.payload.key}
                      out: payloadValue
                """);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals("value", result.variables().get("payloadValue"));
    }


    @Test
    void pairsLifecycleRoutesForRetriedLoopItems() throws Exception {
        var provider = new FlakyProvider();
        var runtime = new TaskRuntime(new TaskRegistry(List.of(provider)), TaskEnvironment.local(
                Path.of("target/control-flow-lifecycle-test")));
        var expressions = new ExpressionService(runtime);
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: flaky
                      in:
                        value: ${item}
                        succeedOnRetry: true
                      retry:
                        times: 1
                        delay: 0
                      loop:
                        items: [first, second]
                """.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var events = new ArrayList<LifecycleEvent>();
        var result = EngineFixture.engine(expressions, 256, runtime, 8,
                delay -> CompletableFuture.completedFuture(null)).run(plan, "default", Map.of(), new StatusCallback() {
            @Override
            public void onEvent(LifecycleEvent event) {
                events.add(event);
            }

            @Override
            public void onTerminal(ProcessResult ignored) {
            }
        });

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        var started = events.stream().filter(event -> event.type() == LifecycleEvent.Type.STEP_STARTED).toList();
        var completed = events.stream().filter(event -> event.type() == LifecycleEvent.Type.STEP_COMPLETED).toList();
        assertTrue(completed.stream().allMatch(completion -> started.stream().anyMatch(start ->
                start.data().get("loopItemIndex").equals(completion.data().get("loopItemIndex"))
                        && start.data().get("retryAttempt").equals(completion.data().get("retryAttempt")))));
    }

    @Test
    void preservesParallelLoopItemIndicesInSyntheticChildLifecycleEvents() throws Exception {
        var provider = new FlakyProvider();
        var runtime = new TaskRuntime(new TaskRegistry(List.of(provider)), TaskEnvironment.local(
                Path.of("target/control-flow-parallel-lifecycle-test")));
        var expressions = new ExpressionService(runtime);
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: flaky
                      in:
                        value: ${item}
                        succeedAt: 1
                      loop:
                        items: [first, second]
                        mode: parallel
                """.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var events = new ArrayList<LifecycleEvent>();
        var result = EngineFixture.engine(expressions, 256, runtime, 8,
                delay -> CompletableFuture.completedFuture(null)).run(plan, "default", Map.of(), new StatusCallback() {
            @Override
            public void onEvent(LifecycleEvent event) {
                events.add(event);
            }

            @Override
            public void onTerminal(ProcessResult ignored) {
            }
        });

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(Set.of(0, 1), events.stream()
                .filter(event -> event.type() == LifecycleEvent.Type.STEP_STARTED)
                .map(event -> event.data().get("loopItemIndex"))
                .collect(java.util.stream.Collectors.toSet()));
    }
    private static ProcessResult run(String source) throws Exception {
        return run(source, Map.of(), new FlakyProvider(), RetryScheduler.SYSTEM);
    }

    private static ProcessResult run(String source, Map<String, Object> input, TaskProvider provider,
                                     RetryScheduler scheduler) throws Exception {
        var runtime = new TaskRuntime(new TaskRegistry(List.of(provider)), TaskEnvironment.local(
                Path.of("target/control-flow-test")));
        var expressions = new ExpressionService(runtime);
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream(
                source.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        return EngineFixture.engine(expressions, 256, runtime, 8, scheduler).run(plan, "default", input, result -> {
        });
    }

    private static final class FlakyProvider implements TaskProvider {
        private int invocations;
        private final List<String> inputs = new ArrayList<>();
        private final List<Integer> retryAttempts = new ArrayList<>();

        @Override
        public Task createTask(Context context, String key) {
            return new Task() {
                @Override
                public TaskResult execute(Variables input) {
                    invocations++;
                    inputs.add(input.getString("value"));
                    var retryAttempt = context.variables().get("__retry_attemptNo", 0, Integer.class);
                    retryAttempts.add(retryAttempt);
                    var succeedOnRetry = input.get("succeedOnRetry", false, Boolean.class);
                    var succeedAt = input.get("succeedAt", 0, Number.class).intValue();
                    if (succeedOnRetry ? retryAttempt == 0 : succeedAt <= 0 || invocations < succeedAt) {
                        return TaskResult.fail("attempt " + invocations + " failed");
                    }
                    return TaskResult.success().value("value", input.get("value"));
                }
            };
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return Task.class;
        }

        @Override
        public boolean hasTask(String key) {
            return "flaky".equals(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("flaky");
        }
    }

    private static final class SideEffectConfigurationProvider implements TaskProvider {

        private final FlakyProvider flaky = new FlakyProvider();
        private final AtomicInteger retryConfigurationCalls = new AtomicInteger();
        private final AtomicInteger loopConfigurationCalls = new AtomicInteger();

        @Override
        public Task createTask(Context context, String key) {
            return switch (key) {
                case "flaky" -> flaky.createTask(context, key);
                case "counter" -> new CounterTask(retryConfigurationCalls, loopConfigurationCalls);
                default -> throw new IllegalArgumentException("Unknown task: " + key);
            };
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return "counter".equals(key) ? CounterTask.class : Task.class;
        }

        @Override
        public boolean hasTask(String key) {
            return "flaky".equals(key) || "counter".equals(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("flaky", "counter");
        }
    }

    public static final class CounterTask implements Task {

        private final AtomicInteger retryConfigurationCalls;
        private final AtomicInteger loopConfigurationCalls;

        public CounterTask(AtomicInteger retryConfigurationCalls, AtomicInteger loopConfigurationCalls) {
            this.retryConfigurationCalls = retryConfigurationCalls;
            this.loopConfigurationCalls = loopConfigurationCalls;
        }

        public int retries() {
            retryConfigurationCalls.incrementAndGet();
            return 1;
        }

        public List<String> items() {
            loopConfigurationCalls.incrementAndGet();
            return List.of("one", "two");
        }

        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success();
        }
    }

    private static final class BlockingProvider implements TaskProvider {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch interrupted = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public Task createTask(Context context, String key) {
            return new Task() {
                @Override
                public TaskResult execute(Variables input) {
                    started.countDown();
                    try {
                        release.await();
                        return TaskResult.success();
                    } catch (InterruptedException e) {
                        interrupted.countDown();
                        Thread.currentThread().interrupt();
                        return TaskResult.fail("interrupted");
                    }
                }
            };
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return Task.class;
        }

        @Override
        public boolean hasTask(String key) {
            return "blocking".equals(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("blocking");
        }
    }
}
