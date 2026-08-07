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

package com.walmartlabs.concord.runtime.v25.runner.engine;

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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredConcurrencyTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void parallelBlockRunsConcurrentlyAndMergesHistoryInSourceOrder() throws Exception {
        var provider = new OrderedProvider();
        var observedHistorySizes = new CopyOnWriteArrayList<Integer>();
        var hook = new TaskRuntime.TaskHook() {
            @Override
            public void before(TaskRuntime.Invocation invocation) {
                observedHistorySizes.add(invocation.history().size());
            }
        };
        var execution = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - parallel:
                        - task: ordered
                          in:
                            name: left
                          out:
                            left: ${result.value}
                        - task: ordered
                          in:
                            name: right
                          out:
                            right: ${result.value}
                      out: [left, right]
                """, Map.of(), provider, 2, List.of(hook));

        assertEquals(ProcessStatus.SUCCEEDED, execution.result().status(), String.valueOf(execution.result().failure()));
        assertEquals("left", execution.result().variables().get("left"));
        assertEquals("right", execution.result().variables().get("right"));
        assertEquals(List.of("left", "right"), execution.runtime().history().stream()
                .map(entry -> entry.result().get("value"))
                .toList());
        assertEquals(List.of(0, 0), observedHistorySizes.stream().sorted().toList());
        assertEquals(2, provider.maximumActive.get());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void nestedParallelIsolatesContainersAndCoalescesNullWrites() throws Exception {
        var provider = new IsolationProvider();
        var execution = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - parallel:
                        - parallel:
                            - task: isolate
                              in: {name: left}
                              out:
                                left: ${result.value}
                            - task: isolate
                              in: {name: right}
                              out:
                                right: ${result.value}
                          out: [left, right]
                        - set:
                            third: 3
                      out: [left, right, third]
                    - parallel:
                        - set:
                            nullable: null
                        - set:
                            nullable: null
                      out: nullable
                """, Map.of("shared", new Object[]{"base"}), provider, 4);

        assertEquals(ProcessStatus.SUCCEEDED, execution.result().status(), String.valueOf(execution.result().failure()));
        assertEquals("left", execution.result().variables().get("left"));
        assertEquals("right", execution.result().variables().get("right"));
        assertEquals(3, execution.result().variables().get("third"));
        assertTrue(execution.result().variables().containsKey("nullable"));
        assertNull(execution.result().variables().get("nullable"));
        assertEquals("base", ((Object[]) execution.result().variables().get("shared"))[0]);
    }

    @Test
    void conflictingParallelWritesFailWithoutPublishingOutput() throws Exception {
        var execution = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - parallel:
                        - set:
                            value: 1
                        - set:
                            value: 2
                      out: value
                """, Map.of(), new EmptyProvider(), 2);

        assertEquals(ProcessStatus.FAILED, execution.result().status());
        assertEquals("PARALLEL_OUTPUT_CONFLICT", execution.result().failure().code());
        assertTrue(execution.result().failure().message().contains("branch 0"));
        assertTrue(execution.result().failure().message().contains("branch 1"));
        assertFalse(execution.result().variables().containsKey("value"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void parallelLoopHonorsAdmissionAndPublishesInputOrder() throws Exception {
        var provider = new OrderedLoopProvider();
        var pending = CompletableFuture.supplyAsync(() -> runUnchecked("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: loopItem
                      in:
                        value: ${item}
                      out:
                        value: ${result.value}
                      loop:
                        items: [0, 1, 2]
                        mode: parallel
                        parallelism: 2
                """, provider, 8));

        try {
            assertTrue(provider.firstTwoStarted.await(5, TimeUnit.SECONDS));
            assertEquals(2, provider.invocations.get());
            provider.release[1].countDown();
            assertTrue(provider.thirdStarted.await(5, TimeUnit.SECONDS));
            provider.release[2].countDown();
            provider.release[0].countDown();

            var execution = pending.get(5, TimeUnit.SECONDS);
            assertEquals(ProcessStatus.SUCCEEDED, execution.result().status(), String.valueOf(execution.result().failure()));
            assertEquals(List.of(0L, 1L, 2L), execution.result().variables().get("value"));
            assertEquals(2, provider.maximumActive.get());
            assertEquals(List.of(0L, 1L, 2L), execution.runtime().history().stream()
                    .map(entry -> entry.result().get("value"))
                    .toList());
        } finally {
            for (var release : provider.release) {
                release.countDown();
            }
            pending.cancel(true);
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void firstParallelLoopFailureStopsAdmissionAndInterruptsSiblings() throws Exception {
        var provider = new FailingLoopProvider();
        var execution = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: failItem
                      in:
                        value: ${item}
                      out:
                        value: ${result.value}
                      loop:
                        items: [0, 1, 2, 3]
                        mode: parallel
                        parallelism: 2
                """, Map.of(), provider, 8);

        assertEquals(ProcessStatus.FAILED, execution.result().status());
        assertEquals("failed-0", execution.result().failure().message());
        assertEquals(0, execution.result().failure().loopItemIndex());
        assertEquals(2, provider.invocations.get());
        assertTrue(provider.interrupted.await(5, TimeUnit.SECONDS));
        assertFalse(execution.result().variables().containsKey("value"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void returnFromParallelBranchCancelsSiblingsAndUnwindsTheFlow() throws Exception {
        var provider = new ReturnProvider();
        var execution = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - parallel:
                        - block:
                            - task: waitForSibling
                            - return
                        - task: blocking
                    - set:
                        unreachable: true
                """, Map.of(), provider, 2);

        assertEquals(ProcessStatus.SUCCEEDED, execution.result().status(), String.valueOf(execution.result().failure()));
        assertTrue(provider.interrupted.await(5, TimeUnit.SECONDS));
        assertFalse(execution.result().variables().containsKey("unreachable"));
    }
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void exitFromParallelBranchCancelsSiblingsAndTerminatesTheProcess() throws Exception {
        var provider = new ReturnProvider();
        var execution = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - parallel:
                        - block:
                            - task: waitForSibling
                            - exit
                        - task: blocking
                    - set:
                        unreachable: true
                """, Map.of(), provider, 2);

        assertEquals(ProcessStatus.SUCCEEDED, execution.result().status(), String.valueOf(execution.result().failure()));
        assertTrue(provider.interrupted.await(5, TimeUnit.SECONDS));
        assertFalse(execution.result().variables().containsKey("unreachable"));
    }
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void externalCancellationInterruptsAndJoinsEveryParallelChild() throws Exception {
        var provider = new CancellationProvider();
        var execution = new AtomicReference<Execution>();
        var failure = new AtomicReference<Throwable>();
        var runner = Thread.ofPlatform().start(() -> {
            try {
                execution.set(run("""
                        configuration:
                          runtime: concord-v2.5
                        flows:
                          default:
                            - parallel:
                                - task: cancellable
                                - task: cancellable
                        """, Map.of(), provider, 2));
            } catch (Throwable e) {
                failure.set(e);
            }
        });

        try {
            assertTrue(provider.started.await(5, TimeUnit.SECONDS));
        } finally {
            runner.interrupt();
            runner.join(5_000);
        }

        assertFalse(runner.isAlive());
        assertNull(failure.get());
        assertEquals(ProcessStatus.CANCELLED, execution.get().result().status());
        assertTrue(provider.interrupted.await(5, TimeUnit.SECONDS));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void uncooperativeParallelTaskReportsEngineFailureAfterGrace() throws Exception {
        var provider = new UncooperativeProvider();
        try {
            var execution = run("""
                    configuration:
                      runtime: concord-v2.5
                    flows:
                      default:
                        - parallel:
                            - task: failAfterSiblingStarts
                            - task: uncooperative
                    """, Map.of(), provider, 2, List.of(), Duration.ofMillis(100));

            assertEquals(ProcessStatus.FAILED, execution.result().status());
            assertEquals("V25_ENGINE", execution.result().failure().code());
            assertTrue(provider.interrupted.await(5, TimeUnit.SECONDS));
        } finally {
            provider.release.countDown();
        }
        assertTrue(provider.finished.await(5, TimeUnit.SECONDS));
    }

    @Test
    @Tag("stress")
    void parallelLoopStressPreservesEveryInputSlot() throws Exception {
        var items = IntStream.range(0, 2_000).boxed().toList();
        var execution = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: echo
                      in:
                        value: ${item}
                      out:
                        value: ${result.value}
                      loop:
                        items: ${items}
                        mode: parallel
                        parallelism: 16
                """, Map.of("items", items), new EchoProvider(), 16);

        assertEquals(ProcessStatus.SUCCEEDED, execution.result().status(), String.valueOf(execution.result().failure()));
        assertEquals(items, execution.result().variables().get("value"));
    }



    private static Execution runUnchecked(String source, TaskProvider provider, int parallelism) {
        try {
            return run(source, Map.of(), provider, parallelism);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Execution run(String source, Map<String, Object> input, TaskProvider provider,
                                 int parallelism) throws Exception {
        return run(source, input, provider, parallelism, List.of(), null);
    }

    private static Execution run(String source, Map<String, Object> input, TaskProvider provider,
                                 int parallelism, List<TaskRuntime.TaskHook> hooks) throws Exception {
        return run(source, input, provider, parallelism, hooks, null);
    }

    private static Execution run(String source, Map<String, Object> input, TaskProvider provider,
                                 int parallelism, List<TaskRuntime.TaskHook> hooks, Duration cancellationGrace)
            throws Exception {
        var runtime = new TaskRuntime(new TaskRegistry(List.of(provider)), TaskEnvironment.local(
                Path.of("target/structured-concurrency-test")), TaskRuntime.Validator.NONE, hooks);
        var expressions = new ExpressionService(runtime);
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream(
                source.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var engine = cancellationGrace == null
                ? EngineFixture.engine(expressions, 256, runtime, parallelism, RetryScheduler.SYSTEM)
                : EngineFixture.engine(expressions, 256, runtime, parallelism, RetryScheduler.SYSTEM, cancellationGrace);
        var result = engine.run(plan, "default", input, ignored -> {
        });
        return new Execution(result, runtime);
    }

    private record Execution(ProcessResult result, TaskRuntime runtime) {
    }

    private static Task executable(TaskBody body) {
        return new Task() {
            @Override
            public TaskResult execute(Variables input) throws Exception {
                return body.execute(input);
            }
        };
    }

    @FunctionalInterface
    private interface TaskBody {
        TaskResult execute(Variables input) throws Exception;
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for concurrent task");
        }
    }

    private abstract static class Provider implements TaskProvider {

        @Override
        public final Task createTask(Context context, String key) {
            return task(context, key);
        }

        protected abstract Task task(Context context, String key);

        @Override
        public final Class<? extends Task> getTaskClass(Context context, String key) {
            return Task.class;
        }

        @Override
        public final boolean hasTask(String key) {
            return names().contains(key);
        }
    }

    private static final class EmptyProvider extends Provider {

        @Override
        protected Task task(Context context, String key) {
            throw new IllegalArgumentException(key);
        }

        @Override
        public Set<String> names() {
            return Set.of();
        }
    }

    private static final class OrderedProvider extends Provider {

        private final CountDownLatch bothStarted = new CountDownLatch(2);
        private final CountDownLatch rightDone = new CountDownLatch(1);
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maximumActive = new AtomicInteger();

        @Override
        protected Task task(Context context, String key) {
            return executable(input -> {
                var current = active.incrementAndGet();
                maximumActive.accumulateAndGet(current, Math::max);
                try {
                    var name = input.getString("name");
                    bothStarted.countDown();
                    await(bothStarted);
                    if ("left".equals(name)) {
                        await(rightDone);
                    } else {
                        rightDone.countDown();
                    }
                    return TaskResult.success().value("value", name);
                } finally {
                    active.decrementAndGet();
                }
            });
        }

        @Override
        public Set<String> names() {
            return Set.of("ordered");
        }
    }

    private static final class IsolationProvider extends Provider {

        private final CountDownLatch bothStarted = new CountDownLatch(2);

        @Override
        protected Task task(Context context, String key) {
            return executable(input -> {
                var name = input.getString("name");
                var shared = context.variables().get("shared", null, Object[].class);
                shared[0] = name;
                bothStarted.countDown();
                await(bothStarted);
                return TaskResult.success().value("value", shared[0]);
            });
        }

        @Override
        public Set<String> names() {
            return Set.of("isolate");
        }
    }

    private static final class EchoProvider extends Provider {

        @Override
        protected Task task(Context context, String key) {
            return executable(input -> TaskResult.success().value("value", input.get("value")));
        }

        @Override
        public Set<String> names() {
            return Set.of("echo");
        }
    }

    private static final class OrderedLoopProvider extends Provider {

        private final CountDownLatch firstTwoStarted = new CountDownLatch(2);
        private final CountDownLatch thirdStarted = new CountDownLatch(1);
        private final CountDownLatch[] release = {
                new CountDownLatch(1), new CountDownLatch(1), new CountDownLatch(1)};
        private final AtomicInteger invocations = new AtomicInteger();
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maximumActive = new AtomicInteger();

        @Override
        protected Task task(Context context, String key) {
            return executable(input -> {
                var value = input.get("value", 0, Number.class).intValue();
                invocations.incrementAndGet();
                var current = active.incrementAndGet();
                maximumActive.accumulateAndGet(current, Math::max);
                try {
                    if (value < 2) {
                        firstTwoStarted.countDown();
                    } else {
                        thirdStarted.countDown();
                    }
                    await(release[value]);
                    return TaskResult.success().value("value", (long) value);
                } finally {
                    active.decrementAndGet();
                }
            });
        }

        @Override
        public Set<String> names() {
            return Set.of("loopItem");
        }
    }

    private static final class FailingLoopProvider extends Provider {

        private final CountDownLatch bothStarted = new CountDownLatch(2);
        private final CountDownLatch interrupted = new CountDownLatch(1);
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        protected Task task(Context context, String key) {
            return executable(input -> {
                var value = input.get("value", 0, Number.class).intValue();
                invocations.incrementAndGet();
                bothStarted.countDown();
                await(bothStarted);
                if (value == 0) {
                    return TaskResult.fail("failed-0");
                }
                try {
                    await(new CountDownLatch(1));
                    return TaskResult.success();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                    throw new CancellationException("sibling failed");
                }
            });
        }

        @Override
        public Set<String> names() {
            return Set.of("failItem");
        }
    }

    private static final class ReturnProvider extends Provider {

        private final CountDownLatch blockingStarted = new CountDownLatch(1);
        private final CountDownLatch interrupted = new CountDownLatch(1);

        @Override
        protected Task task(Context context, String key) {
            return switch (key) {
                case "waitForSibling" -> executable(input -> {
                    await(blockingStarted);
                    return TaskResult.success();
                });
                case "blocking" -> executable(input -> {
                    blockingStarted.countDown();
                    try {
                        await(new CountDownLatch(1));
                        return TaskResult.success();
                    } catch (InterruptedException e) {
                        interrupted.countDown();
                        Thread.currentThread().interrupt();
                        throw new CancellationException("returned sibling");
                    }
                });
                default -> throw new IllegalArgumentException(key);
            };
        }

        @Override
        public Set<String> names() {
            return Set.of("waitForSibling", "blocking");
        }
    }

    private static final class CancellationProvider extends Provider {

        private final CountDownLatch started = new CountDownLatch(2);
        private final CountDownLatch interrupted = new CountDownLatch(2);

        @Override
        protected Task task(Context context, String key) {
            return executable(input -> {
                started.countDown();
                try {
                    await(new CountDownLatch(1));
                    return TaskResult.success();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                    throw new CancellationException("process cancelled");
                }
            });
        }

        @Override
        public Set<String> names() {
            return Set.of("cancellable");
        }
    }
    private static final class UncooperativeProvider extends Provider {

        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch interrupted = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch finished = new CountDownLatch(1);

        @Override
        protected Task task(Context context, String key) {
            return switch (key) {
                case "failAfterSiblingStarts" -> executable(input -> {
                    await(started);
                    return TaskResult.fail("failed");
                });
                case "uncooperative" -> executable(input -> {
                    started.countDown();
                    try {
                        while (true) {
                            try {
                                await(release);
                                return TaskResult.success();
                            } catch (InterruptedException e) {
                                interrupted.countDown();
                            }
                        }
                    } finally {
                        finished.countDown();
                    }
                });
                default -> throw new IllegalArgumentException(key);
            };
        }

        @Override
        public Set<String> names() {
            return Set.of("failAfterSiblingStarts", "uncooperative");
        }
    }
}
