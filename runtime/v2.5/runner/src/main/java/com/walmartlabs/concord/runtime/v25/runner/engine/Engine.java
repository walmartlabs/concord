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

import com.walmartlabs.concord.forms.Form;
import com.walmartlabs.concord.forms.FormField;
import com.walmartlabs.concord.forms.FormOptions;
import com.walmartlabs.concord.runtime.common.FormService;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.runtime.v25.model.Definition25;
import com.walmartlabs.concord.runtime.v25.model.Form25;
import com.walmartlabs.concord.runtime.v25.model.Values;
import com.walmartlabs.concord.runtime.v25.runner.persistence.CheckpointStore;
import com.walmartlabs.concord.runtime.v25.runner.persistence.State25;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import com.walmartlabs.concord.runtime.v25.runner.plan.ExecutionPlan;
import com.walmartlabs.concord.runtime.v25.runner.plan.Instruction;
import com.walmartlabs.concord.runtime.v25.runner.scope.Scope;
import com.walmartlabs.concord.runtime.v25.runner.task.InvocationExecutor;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;

public final class Engine {

    private final ExpressionService expressions;
    private final Duration cancellationGrace;
    private final int maxCallDepth;
    private final TaskRuntime taskRuntime;
    private final int workerParallelism;
    private final RetryScheduler retryScheduler;
    private final CheckpointStore checkpointStore;
    private final FormService formService;

    public Engine(ExpressionService expressions, int maxCallDepth, TaskRuntime taskRuntime, int workerParallelism,
                  RetryScheduler retryScheduler, Duration cancellationGrace, CheckpointStore checkpointStore,
                  FormService formService) {
        if (maxCallDepth < 1) {
            throw new IllegalArgumentException("maxCallDepth must be positive");
        }
        if (workerParallelism < 1) {
            throw new IllegalArgumentException("workerParallelism must be positive");
        }
        if (cancellationGrace == null || cancellationGrace.isNegative() || cancellationGrace.isZero()) {
            throw new IllegalArgumentException("cancellationGrace must be positive");
        }
        this.expressions = Objects.requireNonNull(expressions, "expressions");
        this.cancellationGrace = cancellationGrace;
        this.maxCallDepth = maxCallDepth;
        this.taskRuntime = Objects.requireNonNull(taskRuntime, "taskRuntime");
        this.workerParallelism = workerParallelism;
        this.retryScheduler = Objects.requireNonNull(retryScheduler, "retryScheduler");
        this.checkpointStore = Objects.requireNonNull(checkpointStore, "checkpointStore");
        this.formService = Objects.requireNonNull(formService, "formService");
        taskRuntime.bind(expressions);
    }

    public ProcessResult run(ExecutionPlan plan, String entryPoint, Map<String, Object> input,
                             StatusCallback callback) {
        Objects.requireNonNull(callback, "callback");
        var initial = new LinkedHashMap<String, Object>(plan.configuration().arguments());
        initial.putAll(input);
        var debug = Boolean.TRUE.equals(plan.configuration().values().get("debug"));
        var dryRun = taskRuntime.dryRun();
        var root = Scope.root(initial, plan.flows().keySet(), entryPoint, dryRun, debug, plan);
        return run(new Scheduler(plan, root, entryPoint), callback);
    }

    public ProcessResult resume(ExecutionPlan plan, State25 state, String eventName,
                                Map<String, Object> payload, StatusCallback callback) {
        return resume(plan, state, Set.of(eventName), payload, callback);
    }

    public ProcessResult resume(ExecutionPlan plan, State25 state, Set<String> eventNames,
                                Map<String, Object> payload, StatusCallback callback) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(eventNames, "eventNames");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(callback, "callback");
        if (!plan.id().equals(state.planId())) {
            throw new IllegalArgumentException("State plan identity " + state.planId()
                    + " does not match execution plan " + plan.id());
        }
        var scheduler = restoreScheduler(plan, state);
        var suspensions = scheduler.waits().stream()
                .filter(wait -> eventNames.contains(wait.eventName()))
                .toList();
        if (suspensions.isEmpty()) {
            throw new IllegalArgumentException("No live suspension waits for events " + eventNames);
        }
        for (var suspension : suspensions) {
            scheduler.resume(suspension.eventName(), payload);
            callback.onEvent(lifecycle(plan, suspension, LifecycleEvent.Type.RESUMED, Map.of()));
        }
        return run(scheduler, callback);
    }
    public ProcessResult restart(ExecutionPlan plan, State25 state, StatusCallback callback) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(callback, "callback");
        if (!plan.id().equals(state.planId())) {
            throw new IllegalArgumentException("State plan identity " + state.planId()
                    + " does not match execution plan " + plan.id());
        }
        if (!state.waits().isEmpty()) {
            throw new IllegalArgumentException("Suspended state requires a matching resume event");
        }
        var scheduler = restoreScheduler(plan, state);
        if (state.checkpointName() != null && !"suspend".equals(state.checkpointName())) {
            scheduler.root.commit(Map.of("resumeEvents", List.of(state.checkpointName())));
        }
        return run(scheduler, callback);
    }


    private ProcessResult run(Scheduler scheduler, StatusCallback callback) {
        scheduler.callback = callback;
        SchedulerMessage terminal;
        try (var executor = new InvocationExecutor(workerParallelism, cancellationGrace)) {
            terminal = InvocationExecutor.withCurrent(executor, () -> {
                while (true) {
                    var message = drive(scheduler);
                    if (!(message instanceof SchedulerMessage.Checkpoint checkpoint)) {
                        return message;
                    }
                    try {
                        checkpointStore.save(checkpoint.name(), scheduler.snapshot(ProcessStatus.RUNNING,
                                checkpoint.name(), checkpoint.metadata()));
                    } catch (IOException e) {
                        throw new CheckpointException("Cannot save checkpoint '" + checkpoint.name()
                                + "': " + e.getMessage(), e);
                    }
                    callback.onEvent(lifecycle(scheduler.plan, checkpoint.instruction(),
                            LifecycleEvent.Type.CHECKPOINT_SAVED, null,
                            Map.of("checkpointName", checkpoint.name())));
                }
            });
        } catch (RuntimeException e) {
            terminal = new SchedulerMessage.Failed(scheduler.failureContext(e));
        }
        if (terminal instanceof SchedulerMessage.Suspended suspended) {
            try {
                checkpointStore.save("suspend", scheduler.snapshot(ProcessStatus.SUSPENDED,
                        "suspend", Map.of()));
                callback.onEvent(lifecycle(scheduler.plan, suspended.suspension(),
                        LifecycleEvent.Type.SUSPENDED, Map.of()));
            } catch (IOException | RuntimeException e) {
                terminal = new SchedulerMessage.Failed(scheduler.failureContext(e));
            }
        }
        var root = scheduler.root;
        ProcessResult result;
        if (terminal instanceof SchedulerMessage.Completed || terminal instanceof SchedulerMessage.Exited) {
            try {
                result = ProcessResult.succeeded(root.snapshot(), processOutputs(scheduler.plan, root));
            } catch (RuntimeException e) {
                result = ProcessResult.failed(root.snapshot(), runtimeFailure(e));
            }
        } else if (terminal instanceof SchedulerMessage.Suspended suspended) {
            result = ProcessResult.suspended(root.snapshot(), suspended.suspension());
        } else if (terminal instanceof SchedulerMessage.Cancelled cancelled) {
            var context = cancelled.context();
            var failure = context == null ? null
                    : context.instruction() != null ? failure(context) : runtimeFailure(context.cause());
            result = ProcessResult.cancelled(root.snapshot(), failure);
        } else {
            var failed = (SchedulerMessage.Failed) terminal;
            result = failed.context().instruction() != null
                    ? ProcessResult.failed(root.snapshot(), failure(failed.context()))
                    : ProcessResult.failed(root.snapshot(), runtimeFailure(failed.context().cause()));
        }
        callback.onTerminal(result);
        return result;
    }

    private SchedulerMessage drive(Scheduler scheduler) {
        while (true) {
            var message = scheduler.run();
            if (!(message instanceof SchedulerMessage.Waiting waiting)) {
                return message;
            }
            try {
                waiting.timer().get();
            } catch (InterruptedException e) {
                waiting.timer().cancel(true);
                Thread.currentThread().interrupt();
                return new SchedulerMessage.Cancelled();
            } catch (CancellationException e) {
                return new SchedulerMessage.Cancelled();
            } catch (ExecutionException e) {
                var cause = e.getCause();
                if (cause != waiting.context().cause()) {
                    cause.addSuppressed(waiting.context().cause());
                }
                var context = new FailureContext(waiting.context().instruction(), cause,
                        waiting.context().callStack(), waiting.context().parallelBranchIndex(),
                        waiting.context().loopItemIndex(), waiting.context().retryAttempt());
                return new SchedulerMessage.Failed(context);
            }
        }
    }

    private static LifecycleEvent lifecycle(ExecutionPlan plan, Suspension suspension,
                                            LifecycleEvent.Type type, Map<String, Object> data) {
        var instruction = requiredInstruction(instructionIndex(plan), suspension.instructionId());
        return lifecycle(plan, instruction, type, suspension.eventName(), data);
    }

    private static LifecycleEvent lifecycle(ExecutionPlan plan, Instruction instruction,
                                            LifecycleEvent.Type type, String eventName,
                                            Map<String, Object> data) {
        var range = instruction.sourceRange();
        return new LifecycleEvent(type, plan.id() + ":" + instruction.id(), eventName, instruction.id(),
                range.source(), range.line(), range.column(), instruction.path(), data);
    }

    private static Map<String, Object> stepData(ExecutionPlan plan, Instruction instruction) {
        var range = instruction.sourceRange();
        var data = new LinkedHashMap<String, Object>();
        data.put("processDefinitionId", flowName(instruction.path()));
        data.put("fileName", range.source());
        data.put("line", range.line());
        data.put("column", range.column());
        data.put("description", instruction.sourceType());
        data.put("correlationId", plan.id() + ":" + instruction.id());
        data.put("instructionId", instruction.id());
        data.put("path", instruction.path());
        return data;
    }

    private static String flowName(String path) {
        var prefix = "flows.";
        if (!path.startsWith(prefix)) {
            return path;
        }
        var bracket = path.indexOf('[', prefix.length());
        var dot = path.indexOf('.', prefix.length());
        var end = bracket >= 0 && dot >= 0
                ? Math.min(bracket, dot)
                : Math.max(bracket, dot);
        if (end < 0) {
            end = path.length();
        }
        return path.substring(prefix.length(), end);
    }

    private Scheduler restoreScheduler(ExecutionPlan plan, State25 state) {
        var scheduler = restoreScheduler(plan, state.entryPoint(), state.root(), state.waits());
        taskRuntime.replaceHistory(history(state.history()));
        return scheduler;
    }

    private Scheduler restoreScheduler(ExecutionPlan plan, String entryPoint, State25.FiberState rootState,
                                       List<State25.WaitState> waits) {
        var scopes = new LinkedHashMap<Integer, Scope>();
        for (var scopeState : rootState.scopes()) {
            Scope scope;
            if (scopeState.parentId() == null) {
                scope = Scope.root(scopeState.overlay(), plan.flows().keySet(), scopeState.flowName(),
                        scopeState.dryRun(), scopeState.debug(), plan);
            } else {
                var parent = scopes.get(scopeState.parentId());
                if (parent == null) {
                    throw new IllegalArgumentException("State scope " + scopeState.id()
                            + " references missing parent " + scopeState.parentId());
                }
                scope = parent.child(scopeState.flowName());
                scope.commit(scopeState.overlay());
            }
            scopes.put(scopeState.id(), scope);
        }
        var restoredRoot = requiredScope(scopes, rootState.rootScopeId());
        var instructions = instructionIndex(plan);
        var fiber = new Fiber();
        var scheduler = new Scheduler(plan, restoredRoot, entryPoint, fiber);
        for (var frameState : rootState.continuation()) {
            if (frameState instanceof State25.SequenceState sequence) {
                var sequenceInstructions = sequence.instructionIds().stream()
                        .map(id -> requiredInstruction(instructions, id))
                        .toList();
                var frame = new SequenceFrame(sequenceInstructions, requiredScope(scopes, sequence.scopeId()),
                        sequence.outputTargetId() != null
                                ? requiredScope(scopes, sequence.outputTargetId())
                                : null,
                        sequence.outputDescriptor(), sequence.flow(), sequence.outputInstructionId() != null
                                ? requiredInstruction(instructions, sequence.outputInstructionId()) : null);
                frame.programCounter = sequence.programCounter();
                fiber.continuation.addLast(frame);
            } else if (frameState instanceof State25.StepState step) {
                var instruction = requiredInstruction(instructions, step.instructionId());
                var frame = scheduler.new StepFrame(instruction, requiredScope(scopes, step.parentScopeId()));
                frame.phase = Scheduler.Phase.valueOf(step.phase());
                frame.configurationResolved = step.configurationResolved();
                frame.loop = step.loop() == null ? null
                        : new LoopSpec(step.loop().items(), step.loop().parallel(), step.loop().parallelism());
                frame.retry = step.retry() == null ? null
                        : new RetrySpec(step.retry().times(), Duration.ofMillis(step.retry().delayMillis()),
                        step.retry().input());
                frame.itemIndex = step.itemIndex();
                frame.attempt = step.attempt();
                frame.work = step.workScopeId() != null ? requiredScope(scopes, step.workScopeId()) : null;
                frame.handlerScope = step.handlerScopeId() != null
                        ? requiredScope(scopes, step.handlerScopeId())
                        : null;
                if (step.originalFailure() != null) {
                    frame.originalFailure = restoreFailure(step.originalFailure());
                }
                step.accumulated().forEach((name, values) -> {
                    if (!(values instanceof List<?> list)) {
                        throw new IllegalArgumentException("State loop output '" + name + "' is not a list");
                    }
                    frame.accumulated.put(name, new ArrayList<>(list));
                });
                if (step.waitState() != null) {
                    frame.pending = suspension(step.waitState());
                }
                fiber.continuation.addLast(frame);
                if (step.parallel() != null) {
                    var parallel = step.parallel();
                    frame.parallelRun = scheduler.new ParallelRun(parallel.count(), parallel.limit(),
                            history(parallel.historySnapshot()));
                    frame.parallelRun.next = parallel.nextIndex();
                    for (var child : parallel.children()) {
                        if ("COMPLETED".equals(child.status())) {
                            frame.parallelRun.results[child.index()] = new ChildResult(child.index(),
                                    child.values(), new SchedulerMessage.Completed(), history(child.history()),
                                    null, null);
                        } else if ("SUSPENDED".equals(child.status()) || "RUNNABLE".equals(child.status())) {
                            var childScheduler = restoreScheduler(plan, entryPoint, child.fiber(), child.waits());
                            var request = new ChildRequest(child.index(), child.instructionIds().stream()
                                    .map(id -> requiredInstruction(instructions, id)).toList(),
                                    childScheduler.root, child.branchIndex(), child.loopItemIndex(),
                                    ChildCapture.valueOf(child.capture()), child.outputNames());
                            var message = "SUSPENDED".equals(child.status())
                                    ? new SchedulerMessage.Suspended(childScheduler.pendingSuspension)
                                    : null;
                            frame.parallelRun.results[child.index()] = new ChildResult(child.index(),
                                    child.values(), message, history(child.history()), request, childScheduler);
                        } else {
                            throw new IllegalArgumentException("Unsupported child state '" + child.status() + "'");
                        }
                    }
                }
            } else {
                throw new IllegalArgumentException("Unsupported continuation frame: "
                        + frameState.getClass().getName());
            }
        }
        if (!waits.isEmpty()) {
            if (waits.size() != 1) {
                throw new IllegalArgumentException("Sequential state contains multiple suspension waits");
            }
            var wait = waits.getFirst();
            scheduler.pendingSuspension = suspension(wait);
            scheduler.pendingScope = requiredScope(scopes, wait.scopeId());
            scheduler.currentInstruction = requiredInstruction(instructions, wait.instructionId());
        }
        return scheduler;
    }

        private static List<TaskRuntime.HistoryEntry> history(List<State25.HistoryState> states) {
            return states.stream()
                    .map(state -> new TaskRuntime.HistoryEntry(state.taskName(), state.result(), state.successful()))
                    .toList();
        }

        private static RuntimeException restoreFailure(State25.FailureState state) {
            if (UserDefinedException.class.getName().equals(state.type())) {
                return new UserDefinedException(state.message(), state.payload());
            }
            return new RestoredFailure(state.type(), state.message(), state.payload());
        }

    private static Suspension suspension(State25.WaitState wait) {
        return new Suspension(wait.eventName(), wait.reentrant(), wait.taskName(), wait.payload(),
                wait.instructionId(), wait.path());
    }

    private static Scope requiredScope(Map<Integer, Scope> scopes, int id) {
        var result = scopes.get(id);
        if (result == null) {
            throw new IllegalArgumentException("State references missing scope " + id);
        }
        return result;
    }

    private static Instruction requiredInstruction(Map<Integer, Instruction> instructions, int id) {
        var result = instructions.get(id);
        if (result == null) {
            throw new IllegalArgumentException("State references missing instruction " + id);
        }
        return result;
    }

    private static Map<Integer, Instruction> instructionIndex(ExecutionPlan plan) {
        var result = new LinkedHashMap<Integer, Instruction>();
        plan.flows().values().forEach(flow -> indexInstructions(flow.instructions(), result));
        return result;
    }

    private static void indexInstructions(List<Instruction> instructions, Map<Integer, Instruction> result) {
        for (var instruction : instructions) {
            result.put(instruction.id(), instruction);
            if (instruction.options().containsKey("loop")) {
                var stripped = Scheduler.parallelLoopInstruction(instruction);
                result.put(stripped.id(), stripped);
            }
            instruction.branches().values().forEach(branch -> indexInstructions(branch, result));
        }
    }

    private final class Scheduler {

        private static final String RETRY_ATTEMPT = "__retry_attemptNo";

        private final ExecutionPlan plan;
        private final Scope root;
        private final String entryPoint;
        private final Fiber fiber;

        private Instruction currentInstruction;
        private Suspension pendingSuspension;
        private Scope pendingScope;
        private StatusCallback callback;
        private ParallelRun parentParallel;
        private FailureContext observedFailure;
        private Integer inheritedLoopItemIndex;
        private int flowDepthOffset;


        private Scheduler(ExecutionPlan plan, Scope root, String entryPoint) {
            this(plan, root, entryPoint, new Fiber());
            fiber.push(SequenceFrame.flow(plan.flow(entryPoint).instructions(), root, null, null));
        }

        private Scheduler(ExecutionPlan plan, List<Instruction> instructions, Scope root) {
            this(plan, root, root.flowName(), new Fiber());
            fiber.push(SequenceFrame.sequence(instructions, root, null, null));
        }

        private Scheduler(ExecutionPlan plan, Scope root, String entryPoint, Fiber fiber) {
            this.plan = plan;
            this.root = root;
            this.entryPoint = entryPoint;
            this.fiber = fiber;
        }

        private SchedulerMessage run() {
            if (pendingSuspension != null) {
                return new SchedulerMessage.Suspended(pendingSuspension);
            }
            while (!fiber.done()) {
                if (Thread.currentThread().isInterrupted()) {
                    fiber.clear();
                    return new SchedulerMessage.Cancelled(observedFailure);
                }
                if (parentParallel != null && parentParallel.checkpoint() != null) {
                    return parentParallel.checkpoint();
                }
                try {
                    var frame = fiber.current();
                    if (frame instanceof SequenceFrame sequence) {
                        if (sequence.complete()) {
                            completeFrame((SequenceFrame) fiber.pop());
                            continue;
                        }
                        currentInstruction = sequence.next();
                        if (hasLifecycle(currentInstruction)
                                || currentInstruction.opcode()
                                == com.walmartlabs.concord.runtime.v25.runner.plan.Opcode.SET) {
                            fiber.push(new StepFrame(currentInstruction, sequence.scope()));
                        } else {
                            emitStepStarted(currentInstruction, sequence.scope(), 0, 0);
                            withExpressionContext(currentInstruction, sequence.scope(),
                                    () -> {
                                        executeSimple(currentInstruction, sequence.scope());
                                        emitStepCompleted(currentInstruction, sequence.scope(), 0, 0);
                                        return null;
                                    });
                        }
                    } else {
                        ((StepFrame) frame).advance();
                    }
                } catch (ExitSignal ignored) {
                    fiber.clear();
                    return new SchedulerMessage.Exited();
                } catch (ReturnSignal ignored) {
                    if (!unwindReturn()) {
                        fiber.clear();
                        return new SchedulerMessage.Returned();
                    }
                } catch (SuspendSignal suspended) {
                    pendingSuspension = suspended.suspension;
                    pendingScope = suspended.scope;
                    return new SchedulerMessage.Suspended(suspended.suspension);
                } catch (TaskRuntime.TaskSuspensionException suspended) {
                    pendingSuspension = suspended.suspension();
                    pendingScope = suspended.scope();
                    return new SchedulerMessage.Suspended(suspended.suspension());
                } catch (CheckpointSignal checkpoint) {
                    var message = new SchedulerMessage.Checkpoint(checkpoint.name, checkpoint.metadata,
                            checkpoint.instruction);
                    if (parentParallel != null) {
                        parentParallel.requestCheckpoint(message);
                    }
                    return message;
                } catch (RetryWaitSignal waiting) {
                    return new SchedulerMessage.Waiting(waiting.timer, waiting.context);
                } catch (CancelSignal ignored) {
                    fiber.clear();
                    return new SchedulerMessage.Cancelled(observedFailure);
                } catch (RuntimeException e) {
                    if (e instanceof CancellationException && Thread.currentThread().isInterrupted()) {
                        fiber.clear();
                        return new SchedulerMessage.Cancelled();
                    }
                    if (e instanceof InvocationExecutor.ShutdownException
                            || e instanceof ParallelShutdownException) {
                        fiber.clear();
                        return new SchedulerMessage.Failed(failureContext(currentInstruction, e));
                    }
                    if (e instanceof ChildFailureSignal child
                            && (child.context.cause() instanceof InvocationExecutor.ShutdownException
                            || child.context.cause() instanceof ParallelShutdownException)) {
                        fiber.clear();
                        return new SchedulerMessage.Failed(child.context);
                    }
                    var context = failureContext(currentInstruction, e);
                    try {
                        if (!routeFailure(e)) {
                            fiber.clear();
                            return new SchedulerMessage.Failed(context);
                        }
                    } catch (RetryWaitSignal waiting) {
                        return new SchedulerMessage.Waiting(waiting.timer, waiting.context);
                    } catch (CancelSignal ignored) {
                        fiber.clear();
                        return new SchedulerMessage.Cancelled();
                    } catch (RuntimeException routingFailure) {
                        if (routingFailure != e) {
                            routingFailure.addSuppressed(e);
                        }
                        var routingContext = failureContext(currentInstruction, routingFailure);
                        fiber.clear();
                        return new SchedulerMessage.Failed(routingContext);
                    }
                }
            }
            return new SchedulerMessage.Completed();
        }
        private List<Suspension> waits() {
            var frame = fiber.done() ? null : fiber.current();
            if (frame instanceof StepFrame step && step.parallelRun != null) {
                return step.parallelRun.waits();
            }
            return pendingSuspension == null ? List.of() : List.of(pendingSuspension);
        }

        private boolean waitsFor(String eventName) {
            var frame = fiber.done() ? null : fiber.current();
            if (frame instanceof StepFrame step && step.parallelRun != null) {
                return step.parallelRun.waitsFor(eventName);
            }
            return pendingSuspension != null && pendingSuspension.eventName().equals(eventName);
        }

        private void resume(String eventName, Map<String, Object> payload) {
            var frame = fiber.done() ? null : fiber.current();
            if (frame instanceof StepFrame step && step.parallelRun != null) {
                step.prepareResume(eventName, payload);
            } else {
                if (pendingSuspension == null || !pendingSuspension.eventName().equals(eventName)) {
                    throw new IllegalArgumentException("No live suspension waits for event '" + eventName + "'");
                }
                if (frame instanceof StepFrame step && step.phase == Phase.SUSPENDED) {
                    step.prepareResume(eventName, payload);
                } else {
                    pendingScope.commit(payload);
                }
            }
            pendingSuspension = null;
            pendingScope = null;
        }

        private void emitStepStarted(Instruction instruction, Scope scope, int itemIndex, int attempt) {
            if (callback != null) {
                callback.onEvent(lifecycle(plan, instruction, LifecycleEvent.Type.STEP_STARTED, null,
                        lifecycleData(instruction, scope, itemIndex, attempt)));
            }
        }

        private void emitStepCompleted(Instruction instruction, Scope scope, int itemIndex, int attempt) {
            if (callback != null) {
                callback.onEvent(lifecycle(plan, instruction, LifecycleEvent.Type.STEP_COMPLETED, null,
                        lifecycleData(instruction, scope, itemIndex, attempt)));
            }
        }

        private Map<String, Object> lifecycleData(Instruction instruction, Scope scope, int itemIndex, int attempt) {
            var data = new LinkedHashMap<>(stepData(plan, instruction));
            var rawName = instruction.options().containsKey("name")
                    ? instruction.options().get("name")
                    : instruction.opcode() == com.walmartlabs.concord.runtime.v25.runner.plan.Opcode.TASK
                    ? instruction.value()
                    : null;
            if (rawName != null) {
                data.put("name", expressions.evaluate(rawName, scope));
            }
            if (instruction.options().containsKey("meta")) {
                data.put("meta", expressions.evaluate(instruction.options().get("meta"), scope));
            }
            if (instruction.options().containsKey("loop") || inheritedLoopItemIndex != null) {
                data.put("loopItemIndex", itemIndex);
            }
            if (instruction.options().containsKey("retry")) {
                data.put("retryAttempt", attempt);
            }
            return data;
        }

        private FailureContext failureContext(Throwable cause) {
            return currentInstruction != null
                    ? failureContext(currentInstruction, cause)
                    : new FailureContext(null, cause, List.of(), null, null, null);
        }
        private State25 snapshot(ProcessStatus status, String checkpointName,
                                 Map<String, Object> checkpointMetadata) {
            var scopeIds = new IdentityHashMap<Scope, Integer>();
            var scopes = new ArrayList<State25.ScopeState>();
            var frames = new ArrayList<State25.FrameState>();
            for (var frame : fiber.continuation) {
                if (frame instanceof SequenceFrame sequence) {
                    frames.add(new State25.SequenceState(sequence.instructions.stream()
                            .map(Instruction::id)
                            .toList(), sequence.programCounter, scopeId(sequence.scope, scopeIds, scopes),
                            sequence.outputTarget != null
                                    ? scopeId(sequence.outputTarget, scopeIds, scopes)
                                    : null,
                            sequence.outputDescriptor, sequence.flow,
                            sequence.outputInstruction != null ? sequence.outputInstruction.id() : null));
                } else if (frame instanceof StepFrame step) {
                    var accumulated = new LinkedHashMap<String, Object>();
                    step.accumulated.forEach((name, values) -> accumulated.put(name, Values.list(values)));
                    frames.add(new State25.StepState(step.instruction.id(),
                            scopeId(step.parent, scopeIds, scopes), step.phase.name(), step.configurationResolved,
                            step.loop == null ? null
                                    : new State25.LoopState(step.loop.items(), step.loop.parallel(),
                                    step.loop.parallelism()),
                            step.retry == null ? null
                                    : new State25.RetryState(step.retry.times(), step.retry.delay().toMillis(),
                                    step.retry.input(), null),
                            step.itemIndex, step.attempt,
                            step.work != null ? scopeId(step.work, scopeIds, scopes) : null,
                            step.handlerScope != null ? scopeId(step.handlerScope, scopeIds, scopes) : null,
                            failureState(step.originalFailure), accumulated,
                            step.pending != null ? waitState(step.pending, step.work, scopeIds, scopes) : null,
                            parallelState(step.parallelRun)));
                }
            }

            var rootScopeId = scopeId(root, scopeIds, scopes);
            var waits = pendingSuspension == null
                    ? List.<State25.WaitState>of()
                    : List.of(waitState(pendingSuspension, pendingScope, scopeIds, scopes));
            var fiberStatus = pendingSuspension == null
                    ? State25.FiberStatus.RUNNABLE
                    : State25.FiberStatus.WAITING;
            var fiberState = new State25.FiberState(1, null, fiberStatus, rootScopeId,
                    scopes, frames, List.of());
            var history = taskRuntime.history().stream()
                    .map(entry -> new State25.HistoryState(entry.taskName(), entry.result(), entry.successful()))
                    .toList();
            return new State25(State25.CURRENT_FORMAT, plan.id(), entryPoint, status, null,
                    checkpointName, checkpointMetadata, System.currentTimeMillis(), fiberState, waits,
                    history);
        }
        private TaskRuntime.StepContext taskContext(Instruction instruction, Scope scope, int itemIndex, int attempt) {
            var range = instruction.sourceRange();
            return new TaskRuntime.StepContext(instruction.path(), plan.id() + ":" + instruction.id(), range.source(),
                    range.line(), range.column(), lifecycleData(instruction, scope, itemIndex, attempt),
                    callback != null ? callback.activeLogSegment() : null);
        }

        private State25.ParallelState parallelState(ParallelRun parallel) {
            if (parallel == null) {
                return null;
            }
            var children = new ArrayList<State25.ChildState>();
            for (var child : parallel.results) {
                if (child == null) {
                    continue;
                }
                var history = child.history().stream()
                        .map(entry -> new State25.HistoryState(entry.taskName(), entry.result(),
                                entry.successful()))
                        .toList();
                if (child.message() instanceof SchedulerMessage.Completed) {
                    children.add(new State25.ChildState(child.index(), "COMPLETED", child.values(),
                            null, null, List.of(), List.of(), null, null, null, List.of(), history));
                } else if (child.message() instanceof SchedulerMessage.Suspended || child.message() == null) {
                    if (child.scheduler() == null || child.request() == null) {
                        throw new IllegalStateException("Parallel child is not at a durable safe point");
                    }
                    var suspended = child.message() instanceof SchedulerMessage.Suspended;
                    var childState = child.scheduler().snapshot(
                            suspended ? ProcessStatus.SUSPENDED : ProcessStatus.RUNNING, null, Map.of());
                    children.add(new State25.ChildState(child.index(),
                            suspended ? "SUSPENDED" : "RUNNABLE", child.values(),
                            null, childState.root(), childState.waits(),
                            child.request().instructions().stream().map(Instruction::id).toList(),
                            child.request().branchIndex(), child.request().loopItemIndex(),
                            child.request().capture().name(), child.request().outputNames(), history));
                } else {
                    throw new IllegalStateException("Cannot persist parallel child state "
                            + child.message().getClass().getSimpleName());
                }
            }
            var historySnapshot = parallel.historySnapshot.stream()
                    .map(entry -> new State25.HistoryState(entry.taskName(), entry.result(), entry.successful()))
                    .toList();
            return new State25.ParallelState(parallel.count, parallel.limit, parallel.next, children,
                    historySnapshot);
        }

        private State25.WaitState waitState(Suspension suspension, Scope scope,
                                            IdentityHashMap<Scope, Integer> scopeIds,
                                            List<State25.ScopeState> scopes) {
            var instruction = requiredInstruction(instructionIndex(plan), suspension.instructionId());
            var range = instruction.sourceRange();
            return new State25.WaitState(suspension.eventName(), suspension.reentrant(),
                    suspension.taskName(), suspension.payload(), suspension.instructionId(), range.source(),
                    range.line(), range.column(), suspension.path(), scopeId(scope, scopeIds, scopes), 1);
        }

        private int scopeId(Scope scope, IdentityHashMap<Scope, Integer> ids,
                            List<State25.ScopeState> states) {
            var existing = ids.get(scope);
            if (existing != null) {
                return existing;
            }
            var parentId = scope.parent() != null ? scopeId(scope.parent(), ids, states) : null;
            var id = states.size();
            ids.put(scope, id);
            states.add(new State25.ScopeState(id, parentId, scope.flowName(), scope.dryRun(),
                    scope.debug(), scope.localValues()));
            return id;
        }

        private State25.FailureState failureState(Throwable failure) {
            if (failure == null) {
                return null;
            }
            var payload = failure instanceof UserDefinedException user && user.getPayload() != null
                    ? user.getPayload()
                    : Map.<String, Object>of();
            return new State25.FailureState(failure.getClass().getName(), safeMessage(failure), payload);
        }



        private boolean hasLifecycle(Instruction instruction) {
            return Set.of(com.walmartlabs.concord.runtime.v25.runner.plan.Opcode.EXPR,
                    com.walmartlabs.concord.runtime.v25.runner.plan.Opcode.TASK,
                    com.walmartlabs.concord.runtime.v25.runner.plan.Opcode.SCRIPT,
                    com.walmartlabs.concord.runtime.v25.runner.plan.Opcode.CALL,
                    com.walmartlabs.concord.runtime.v25.runner.plan.Opcode.GROUP,
                    com.walmartlabs.concord.runtime.v25.runner.plan.Opcode.PARALLEL,
                    com.walmartlabs.concord.runtime.v25.runner.plan.Opcode.FORM).contains(instruction.opcode());
        }

        private <T> T withExpressionContext(Instruction instruction, Scope scope, Callable<T> action) {
            return taskRuntime.withCurrentInstruction(instruction, () -> taskRuntime.withNestedFlowExecutor(
                    (flowName, input) -> executeNestedFlow(flowName, input, scope), action));
        }

        @SuppressWarnings("unchecked")
        private void executeSimple(Instruction instruction, Scope scope) {
            switch (instruction.opcode()) {
                case LOG -> executeTask(instruction, scope, "log", Map.of("msg", instruction.value()), null);
                case LOG_YAML -> executeTask(instruction, scope, "log",
                        Map.of("msg", instruction.value(), "format", "yaml"), null);
                case SET -> executeSet(instruction, scope);
                case IF -> executeIf(instruction, scope);
                case SWITCH -> executeSwitch(instruction, scope);
                case RETURN -> executeReturn();
                case EXIT -> throw ExitSignal.INSTANCE;
                case SUSPEND -> {
                    var eventName = expressions.evaluate(instruction.value(), scope, String.class);
                    if (eventName == null || eventName.isBlank()) {
                        throw new IllegalArgumentException("suspend requires a non-empty event name");
                    }
                    throw new SuspendSignal(new Suspension(eventName, false, null, Map.of(), instruction.id(),
                            instruction.path()), scope);
                }
                case THROW -> executeThrow(instruction, scope);
                case CHECKPOINT -> executeCheckpoint(instruction, scope);
                case FORM -> throw new IllegalStateException("Lifecycle step dispatched as a simple step: form");
                case EXPR, TASK, SCRIPT, CALL, GROUP, PARALLEL ->
                        throw new IllegalStateException("Lifecycle step dispatched as a simple step: "
                                + instruction.sourceType());
            }
        }

        @SuppressWarnings("unchecked")
        private void executeCheckpoint(Instruction instruction, Scope scope) {
            var evaluated = expressions.evaluate(instruction.value(), scope);
            if (evaluated == null || evaluated instanceof Map<?, ?> || evaluated instanceof Iterable<?>
                    || evaluated.getClass().isArray()) {
                throw new IllegalArgumentException("checkpoint requires a non-null scalar name");
            }
            var name = evaluated.toString();
            if ("suspend".equals(name)) {
                throw new IllegalArgumentException("checkpoint name 'suspend' is reserved");
            }
            if (name.isBlank()) {
                throw new IllegalArgumentException("checkpoint requires a non-empty name");
            }
            var rawMetadata = expressions.evaluate(instruction.options().get("meta"), scope);
            if (rawMetadata != null && !(rawMetadata instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("checkpoint.meta must resolve to a mapping");
            }
            var metadata = rawMetadata == null
                    ? Map.<String, Object>of()
                    : Values.map((Map<String, Object>) rawMetadata);
            throw new CheckpointSignal(name, metadata, instruction);
        }

        private void executeForm(Instruction instruction, Scope scope) {
            var name = expressions.evaluate(instruction.value(), scope, String.class);
            if (name == null || !name.matches("^[A-Za-z0-9_ $]+$")) {
                throw new IllegalArgumentException("Form name must match ^[A-Za-z0-9_ $]+$");
            }
            var values = serializableMap(expressions.evaluate(instruction.options().get("values"), scope),
                    instruction.path() + ".values");
            var fields = formFields(instruction, scope, name, values);
            var runAs = serializableMap(expressions.evaluate(instruction.options().get("runAs"), scope),
                    instruction.path() + ".runAs");
            var options = FormOptions.builder()
                    .isYield(Boolean.TRUE.equals(expressions.evaluate(instruction.options().get("yield"), scope)))
                    .saveSubmittedBy(Boolean.TRUE.equals(
                            expressions.evaluate(instruction.options().get("saveSubmittedBy"), scope)))
                    .runAs(runAs)
                    .extraValues(values)
                    .build();
            if (Thread.currentThread().isInterrupted()) {
                throw CancelSignal.INSTANCE;
            }
            var eventName = UUID.randomUUID().toString();
            formService.save(Form.builder()
                    .name(name)
                    .eventName(eventName)
                    .options(options)
                    .fields(fields)
                    .build());
            throw new SuspendSignal(new Suspension(eventName, false, null, Map.of("formName", name),
                    instruction.id(), instruction.path()), scope);
        }

        private List<FormField> formFields(Instruction instruction, Scope scope, String formName,
                                           Map<String, Serializable> values) {
            var raw = expressions.evaluate(instruction.options().get("fields"), scope);
            if (raw == null) {
                var definition = plan.forms().get(formName);
                if (definition == null) {
                    throw new IllegalArgumentException("Form not found: " + formName);
                }
                if (definition instanceof Form25 form && form.dynamic()) {
                    raw = expressions.evaluate(form.fieldsExpression(), scope);
                } else {
                    return definition.fields().stream()
                            .map(field -> formField(field.name(), field.type(), field.label(),
                                    field.defaultValue(), field.allowedValue(), field.options(), values, scope,
                                    instruction.path() + ".fields." + field.name()))
                            .toList();
                }
            }
            if (!(raw instanceof Iterable<?> items)) {
                throw new IllegalArgumentException("form.fields must resolve to a list");
            }
            var result = new ArrayList<FormField>();
            var index = 0;
            for (var item : items) {
                if (!(item instanceof Map<?, ?> mapping) || mapping.size() != 1) {
                    throw new IllegalArgumentException("Form field at " + instruction.path() + ".fields["
                            + index + "] must contain exactly one field name");
                }
                var entry = mapping.entrySet().iterator().next();
                var fieldName = String.valueOf(entry.getKey());
                if (entry.getValue() instanceof String type) {
                    result.add(formField(fieldName, type, null, null, null, Map.of(), values, scope,
                            instruction.path() + ".fields[" + index + "]"));
                } else if (entry.getValue() instanceof Map<?, ?> options) {
                    var type = options.get("type");
                    if (!(type instanceof String)) {
                        throw new IllegalArgumentException("Form field '" + fieldName + "' requires a string type");
                    }
                    var fieldOptions = new LinkedHashMap<String, Object>();
                    options.forEach((key, value) -> {
                        var optionName = String.valueOf(key);
                        if (!Set.of("type", "label", "value", "default", "allow").contains(optionName)) {
                            fieldOptions.put(optionName, value);
                        }
                    });
                    var defaultValue = options.containsKey("value") ? options.get("value") : options.get("default");
                    result.add(formField(fieldName, type.toString(), options.get("label"), defaultValue,
                            options.get("allow"), fieldOptions, values, scope,
                            instruction.path() + ".fields[" + index + "]"));
                } else {
                    throw new IllegalArgumentException("Form field '" + fieldName
                            + "' must be a type string or mapping");
                }
                index++;
            }
            return List.copyOf(result);
        }

        private FormField formField(String name, String rawType, Object rawLabel, Object rawDefault,
                                    Object rawAllowed, Map<?, ?> rawOptions,
                                    Map<String, Serializable> values, Scope scope, String path) {
            var type = rawType;
            var cardinality = FormField.Cardinality.ONE_AND_ONLY_ONE;
            if (type.endsWith("?")) {
                type = type.substring(0, type.length() - 1);
                cardinality = FormField.Cardinality.ONE_OR_NONE;
            } else if (type.endsWith("+")) {
                type = type.substring(0, type.length() - 1);
                cardinality = FormField.Cardinality.AT_LEAST_ONE;
            } else if (type.endsWith("*")) {
                type = type.substring(0, type.length() - 1);
                cardinality = FormField.Cardinality.ANY;
            }
            var label = rawLabel == null ? null : expressions.evaluate(rawLabel, scope, String.class);
            var defaultValue = values.containsKey(name)
                    ? values.get(name)
                    : serializable(expressions.evaluate(rawDefault, scope), path + ".value");
            var allowedValue = serializable(expressions.evaluate(rawAllowed, scope), path + ".allow");
            var options = new LinkedHashMap<String, Serializable>();
            rawOptions.forEach((key, value) -> options.put(String.valueOf(key),
                    serializable(expressions.evaluate(value, scope), path + "." + key)));
            return FormField.builder()
                    .name(name)
                    .label(label)
                    .type(type)
                    .cardinality(cardinality)
                    .defaultValue(defaultValue)
                    .allowedValue(allowedValue)
                    .options(options)
                    .build();
        }

        private Map<String, Serializable> serializableMap(Object value, String path) {
            if (value == null) {
                return Map.of();
            }
            if (!(value instanceof Map<?, ?> mapping)) {
                throw new IllegalArgumentException(path + " must resolve to a mapping");
            }
            var result = new LinkedHashMap<String, Serializable>();
            mapping.forEach((key, item) -> result.put(String.valueOf(key), serializable(item, path + "." + key)));
            return Collections.unmodifiableMap(result);
        }

        private Serializable serializable(Object value, String path) {
            if (value == null) {
                return null;
            }
            if (value instanceof Serializable serializable) {
                return serializable;
            }
            throw new IllegalArgumentException(path + " must resolve to a serializable value, got "
                    + value.getClass().getName());
        }


        private void executeTask(Instruction instruction, Scope scope, String fixedTaskName, Object rawInput,
                                 Object retryInput) {
            executeTask(instruction, scope, fixedTaskName, rawInput, retryInput, 0, 0);
        }

        private void executeTask(Instruction instruction, Scope scope, String fixedTaskName, Object rawInput,
                                 Object retryInput, int itemIndex, int attempt) {
            var evaluatedName = fixedTaskName != null ? fixedTaskName : expressions.evaluate(instruction.value(), scope);
            if (evaluatedName == null || evaluatedName.toString().isBlank()) {
                throw new IllegalArgumentException("Task name must not be empty");
            }
            var outcome = taskRuntime.withInvocationContext(taskContext(instruction, scope, itemIndex, attempt),
                    () -> taskRuntime.withNestedFlowExecutor(
                            (flowName, input) -> executeNestedFlow(flowName, input, scope),
                            () -> taskRuntime.invoke(plan, instruction, evaluatedName.toString(), rawInput, retryInput,
                                    scope)));
            var ignored = outcome.failure() != null
                    && Boolean.TRUE.equals(expressions.evaluate(instruction.options().get("ignoreErrors"), scope));
            if (outcome.failure() != null && !ignored) {
                throwUnchecked(outcome.failure());
            }
            if (outcome.failure() == null || ignored) {
                publishResult(scope, instruction.options().get("out"), outcome.values());
            }
            if (outcome.suspension() != null) {
                throw new SuspendSignal(outcome.suspension(), scope);
            }
        }

        private Serializable executeNestedFlow(String flowName, Map<String, Object> input, Scope caller) {
            if (flowName == null || flowName.isBlank()) {
                throw new IllegalArgumentException("Nested flow name must not be empty");
            }
            if (flowDepthOffset + fiber.flowDepth() - 1 >= maxCallDepth) {
                throw new IllegalStateException("Maximum flow call depth of " + maxCallDepth + " exceeded");
            }
            var flow = plan.flow(flowName);
            var nestedScope = caller.child(flowName);
            nestedScope.commit(input);
            var nested = new Scheduler(plan, nestedScope, flowName);
            nested.callback = callback;
            nested.flowDepthOffset = flowDepthOffset + fiber.flowDepth();
            var terminal = drive(nested);
            if (terminal instanceof SchedulerMessage.Completed || terminal instanceof SchedulerMessage.Returned) {
                var result = nestedScope.localValues().get("result");
                if (result != null && !(result instanceof Serializable)) {
                    throw new IllegalStateException("Nested flow result is not serializable: "
                            + result.getClass().getName());
                }
                return (Serializable) result;
            }
            if (terminal instanceof SchedulerMessage.Exited) {
                throw ExitSignal.INSTANCE;
            }
            if (terminal instanceof SchedulerMessage.Failed failed) {
                throwUnchecked(failed.context().cause());
            }
            if (terminal instanceof SchedulerMessage.Suspended suspended) {
                throw new IllegalStateException("Nested flow '" + flowName + "' cannot suspend on event '"
                        + suspended.suspension().eventName() + "'");
            }
            if (terminal instanceof SchedulerMessage.Checkpoint checkpoint) {
                throw new IllegalStateException("Nested flow '" + flowName + "' cannot checkpoint at "
                        + checkpoint.instruction().path());
            }
            throw new IllegalStateException("Nested flow '" + flowName + "' was cancelled");
        }

        private void executeScript(Instruction instruction, Scope scope, Object retryInput,
                                   int itemIndex, int attempt) {
            var outcome = taskRuntime.withInvocationContext(taskContext(instruction, scope, itemIndex, attempt),
                    () -> taskRuntime.withNestedFlowExecutor(
                            (flowName, input) -> executeNestedFlow(flowName, input, scope),
                            () -> taskRuntime.invokeScript(plan, instruction, retryInput, scope)));
            if (outcome.failure() != null) {
                throwUnchecked(outcome.failure());
            }
            publishResult(scope, instruction.options().get("out"), outcome.values());
            if (outcome.suspension() != null) {
                throw new SuspendSignal(outcome.suspension(), scope);
            }
        }

        private void executeSet(Instruction instruction, Scope scope) {
            if (!(instruction.value() instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException("set requires a mapping");
            }
            for (var entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String name)) {
                    throw new IllegalArgumentException("set variable names must be strings");
                }
                scope.set(name, expressions.evaluate(entry.getValue(), scope));
            }
        }

        private void executeIf(Instruction instruction, Scope scope) {
            var value = expressions.evaluate(instruction.value(), scope);
            var branch = condition(value, instruction.path()) ? "then" : "else";
            fiber.push(SequenceFrame.sequence(instruction.branch(branch), scope, null, null));
        }

        private void executeSwitch(Instruction instruction, Scope scope) {
            var selector = expressions.evaluate(instruction.value(), scope);
            List<Instruction> selected = null;
            for (var entry : instruction.branches().entrySet()) {
                if ("default".equals(entry.getKey())) {
                    continue;
                }
                var caseValue = expressions.evaluate(entry.getKey(), scope);
                if (Objects.equals(selector, caseValue)) {
                    selected = entry.getValue();
                    break;
                }
            }
            if (selected == null) {
                selected = instruction.branch("default");
            }
            fiber.push(SequenceFrame.sequence(selected, scope, null, null));
        }

        @SuppressWarnings("unchecked")
        private void executeCall(Instruction instruction, Scope caller, Object retryInput) {
            if (flowDepthOffset + fiber.flowDepth() - 1 >= maxCallDepth) {
                throw new IllegalStateException("Maximum flow call depth of " + maxCallDepth + " exceeded");
            }
            var evaluatedName = expressions.evaluate(instruction.value(), caller);
            if (!(evaluatedName instanceof String flowName) || flowName.isBlank()) {
                throw new IllegalArgumentException("call must resolve to a non-empty flow name");
            }
            var callee = caller.child(flowName);
            applyCallInput(instruction.options().get("in"), caller, callee);
            applyCallInput(retryInput, caller, callee);
            fiber.push(SequenceFrame.flow(plan.flow(flowName).instructions(), callee, caller,
                    instruction.options().get("out"), instruction));
        }

        @SuppressWarnings("unchecked")
        private void applyCallInput(Object rawInput, Scope caller, Scope callee) {
            if (rawInput == null) {
                return;
            }
            var evaluatedInput = expressions.evaluate(rawInput, caller);
            if (!(evaluatedInput instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException("call.in must resolve to a mapping");
            }
            callee.commit((Map<String, Object>) values);
        }

        private void executeReturn() {
            if (!unwindReturn()) {
                throw ReturnSignal.INSTANCE;
            }
        }

        private boolean unwindReturn() {
            while (!fiber.done()) {
                var frame = fiber.pop();
                if (frame instanceof SequenceFrame sequence && sequence.flow()) {
                    completeFrame(sequence);
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings("unchecked")
        private void executeThrow(Instruction instruction, Scope scope) {
            var value = expressions.evaluate(instruction.value(), scope);
            if (value instanceof Throwable error) {
                throwUnchecked(error);
            }
            if (value instanceof Map<?, ?> values) {
                var message = values.get("message");
                var payload = values.get("payload");
                var mappedPayload = new LinkedHashMap<String, Object>();
                if (payload instanceof Map<?, ?> items) {
                    items.forEach((key, item) -> mappedPayload.put(String.valueOf(key), item));
                }
                throw new UserDefinedException(message != null ? message.toString() : values.toString(),
                        mappedPayload.isEmpty() ? null : mappedPayload);
            }
            throw new UserDefinedException(String.valueOf(value));
        }

        private void completeFrame(SequenceFrame frame) {
            if (frame.outputTarget() != null && frame.outputDescriptor() != null) {
                currentInstruction = frame.outputInstruction() != null ? frame.outputInstruction()
                        : currentInstruction;
                publishScopeOutputs(frame.outputTarget(), frame.scope(), frame.outputDescriptor());
            }
        }

        private boolean routeFailure(RuntimeException failure) {
            var error = failure;
            while (!fiber.done()) {
                var frame = fiber.current();
                if (frame instanceof StepFrame step) {
                    if (step.acceptFailure(error)) {
                        return true;
                    }
                    fiber.pop();
                } else {
                    fiber.pop();
                }
            }
            return false;
        }

        private final class StepFrame implements FrameNode {

            private final Instruction instruction;
            private final Scope parent;
            private final boolean simple;
            private final Map<String, List<Object>> accumulated = new LinkedHashMap<>();

            private Phase phase = Phase.NEW;
            private boolean configurationResolved;
            private LoopSpec loop;
            private RetrySpec retry;
            private int itemIndex;
            private int attempt;
            private Scope work;
            private Scope handlerScope;
            private Throwable originalFailure;
            private Suspension pending;
            private Map<String, Object> resumePayload;
            private ParallelRun parallelRun;

            private StepFrame(Instruction instruction, Scope parent) {
                this.instruction = instruction;
                this.parent = parent;
                this.simple = !hasLifecycle(instruction);
            }

            private void advance() {
                currentInstruction = instruction;
                switch (phase) {
                    case NEW -> {
                        if (!configurationResolved) {
                            if (!simple) {
                                loop = loop(instruction, parent);
                                if (loop != null && loop.parallel()) {
                                    retry = null;
                                } else {
                                    retry = retry(instruction, parent);
                                }
                            }
                            configurationResolved = true;
                        }
                        if (loop != null && loop.items().isEmpty()) {
                            var outputs = new LinkedHashMap<String, Object>();
                            outputNames(instruction.options().get("out"), parent).forEach(name ->
                                    outputs.put(name, List.of()));
                            parent.commit(outputs);
                            fiber.pop();
                        } else if (loop != null && loop.parallel()) {
                            work = parent.child(parent.flowName());
                            runParallelLoop();
                        } else {
                            startAttempt();
                        }
                    }
                    case WAITING -> completeAttempt();
                    case SUSPENDED -> throw new IllegalStateException("Suspended step dispatched without a resume event");
                    case RESUMING -> resumeAttempt();
                    case PARALLEL -> resumeParallel();
                    case HANDLING -> completeHandler();
                }
            }

            private void startAttempt() {
                work = parent.child(parent.flowName());
                if (loop != null) {
                    work.set("item", loop.items().get(itemIndex));
                    work.set("itemIndex", itemIndex);
                    work.set("items", loop.items());
                }
                if (retry != null) {
                    work.set(RETRY_ATTEMPT, attempt);
                }
                emitStepStarted(instruction, work, lifecycleItemIndex(), attempt);
                var retryInput = retry != null && attempt > 0 ? retry.input() : null;
                try {
                    var waiting = dispatch(work, retryInput);
                    if (waiting) {
                        phase = Phase.WAITING;
                    } else {
                        completeAttempt();
                    }
                } catch (SuspendSignal suspended) {
                    pending = suspended.suspension;
                    phase = Phase.SUSPENDED;
                    throw suspended;
                } catch (TaskRuntime.TaskSuspensionException suspended) {
                    pending = suspended.suspension();
                    phase = Phase.SUSPENDED;
                    throw new SuspendSignal(suspended.suspension(), suspended.scope());
                }
            }

            private void prepareResume(String eventName, Map<String, Object> payload) {
                if (parallelRun != null) {
                    if (!parallelRun.resume(eventName, payload)) {
                        throw new IllegalArgumentException("No live suspension waits for event '" + eventName + "'");
                    }
                    phase = Phase.PARALLEL;
                    return;
                }
                resumePayload = Values.map(payload);
                phase = Phase.RESUMING;
            }

            private void runParallelLoop() {
                try {
                    if (parallelRun == null) {
                        parallelRun = new ParallelRun(loop.items().size(), loop.parallelism());
                    }
                    executeParallelLoop(instruction, parent, work, loop, parallelRun);
                    parallelRun = null;
                    parent.commit(userLocals(work));
                    fiber.pop();
                } catch (SuspendSignal suspended) {
                    pending = suspended.suspension;
                    phase = Phase.SUSPENDED;
                    throw suspended;
                }
            }

            private void resumeParallel() {
                try {
                    if (loop != null && loop.parallel()) {
                        runParallelLoop();
                    } else {
                        executeParallelBlock(instruction, work, parallelRun);
                        parallelRun = null;
                        pending = null;
                        phase = Phase.WAITING;
                        completeAttempt();
                    }
                } catch (SuspendSignal suspended) {
                    pending = suspended.suspension;
                    phase = Phase.SUSPENDED;
                    throw suspended;
                }
            }

            private void resumeAttempt() {
                if (pending.reentrant()) {
                    try {
                        var outcome = taskRuntime.withInvocationContext(
                                taskContext(instruction, work, itemIndex, attempt),
                                () -> taskRuntime.withNestedFlowExecutor(
                                        (flowName, input) -> executeNestedFlow(flowName, input, work),
                                        () -> taskRuntime.resume(plan, instruction, pending, resumePayload, work)));
                        var ignored = outcome.failure() != null && Boolean.TRUE.equals(
                                expressions.evaluate(instruction.options().get("ignoreErrors"), work));
                        if (outcome.failure() != null && !ignored) {
                            throwUnchecked(outcome.failure());
                        }
                        if (outcome.failure() == null || ignored) {
                            publishResult(work, instruction.options().get("out"), outcome.values());
                        }
                        if (outcome.suspension() != null) {
                            pending = outcome.suspension();
                            phase = Phase.SUSPENDED;
                            throw new SuspendSignal(pending, work);
                        }
                    } finally {
                        resumePayload = null;
                    }
                } else {
                    work.commit(resumePayload);
                    resumePayload = null;
                }
                pending = null;
                phase = Phase.WAITING;
                completeAttempt();
            }

            private boolean dispatch(Scope scope, Object retryInput) {
                return withExpressionContext(instruction, scope, () -> dispatchWithin(scope, retryInput));
            }

            private boolean dispatchWithin(Scope scope, Object retryInput) {
                switch (instruction.opcode()) {
                    case EXPR -> publishResult(scope, instruction.options().get("out"),
                            expressions.evaluate(instruction.value(), scope));
                    case TASK -> executeTask(instruction, scope, null, instruction.options().get("in"), retryInput,
                            itemIndex, attempt);
                    case SCRIPT -> executeScript(instruction, scope, retryInput, itemIndex, attempt);
                    case FORM -> executeForm(instruction, scope);
                    case CALL -> {
                        executeCall(instruction, scope, retryInput);
                        return true;
                    }
                    case GROUP -> {
                        var child = scope.child(scope.flowName());
                        fiber.push(SequenceFrame.sequence(instruction.branch("body"), child, scope,
                                instruction.options().get("out"), instruction));
                        return true;
                    }
                    case PARALLEL -> {
                        if (parallelRun == null) {
                            parallelRun = new ParallelRun(instruction.branch("body").size(),
                                    Math.min(workerParallelism, instruction.branch("body").size()));
                        }
                        executeParallelBlock(instruction, scope, parallelRun);
                        parallelRun = null;
                    }
                    default -> {
                        executeSimple(instruction, scope);
                        return instruction.opcode() == com.walmartlabs.concord.runtime.v25.runner.plan.Opcode.IF
                                || instruction.opcode() == com.walmartlabs.concord.runtime.v25.runner.plan.Opcode.SWITCH;
                    }
                }
                return false;
            }

            private void completeAttempt() {
                emitStepCompleted(instruction, work, lifecycleItemIndex(), attempt);
                if (loop == null) {
                    parent.commit(userLocals(work));
                    fiber.pop();
                    return;
                }
                appendOutputs(work);
                itemIndex++;
                attempt = 0;
                if (itemIndex < loop.items().size()) {
                    phase = Phase.NEW;
                    return;
                }
                var outputs = new LinkedHashMap<String, Object>();
                accumulated.forEach((name, values) -> outputs.put(name,
                        Collections.unmodifiableList(new ArrayList<>(values))));
                parent.commit(outputs);
                fiber.pop();
            }

            private boolean acceptFailure(RuntimeException failure) {
                if (phase == Phase.HANDLING) {
                    if (originalFailure != null && originalFailure != failure) {
                        failure.addSuppressed(originalFailure);
                    }
                    return false;
                }
                if (retry != null && attempt < retry.times()) {
                    pending = null;
                    attempt++;
                    phase = Phase.NEW;
                    var timer = retryScheduler.delay(retry.delay()).toCompletableFuture();
                    throw new RetryWaitSignal(timer, failureContext(instruction, failure));
                }
                var handler = instruction.branch("error");
                if (!handler.isEmpty()) {
                    parallelRun = null;
                    observedFailure = null;
                    originalFailure = failure;
                    handlerScope = work.child(work.flowName());
                    handlerScope.set("lastError", lastError(failure, instruction));
                    phase = Phase.HANDLING;
                    fiber.push(SequenceFrame.sequence(handler, handlerScope, null, null));
                    return true;
                }
                return false;
            }

            private void completeHandler() {
                parent.commit(declaredOutputs(handlerScope, instruction.options().get("out"), parent));
                fiber.pop();
            }

            private void appendOutputs(Scope source) {
                var outputs = declaredOutputs(source, instruction.options().get("out"), parent);
                outputNames(instruction.options().get("out"), parent).forEach(name ->
                        accumulated.computeIfAbsent(name, ignored -> new ArrayList<>())
                                .add(outputs.get(name)));
            }

            private int lifecycleItemIndex() {
                return inheritedLoopItemIndex != null ? inheritedLoopItemIndex : itemIndex;
            }
        }
        private void executeParallelBlock(Instruction instruction, Scope target, ParallelRun run) {
            var branches = instruction.branch("body");
            var batch = runChildren(run, index -> {
                var branchScope = target.fork();
                return new ChildRequest(index, List.of(branches.get(index)), branchScope, index, null,
                        ChildCapture.OVERLAY, List.of());
            });
            publishChildHistories(batch);
            propagateChildTerminal(batch);
            observedFailure = null;

            var merged = target.child(target.flowName());
            var writes = new LinkedHashMap<String, Object>();
            var writers = new LinkedHashMap<String, Integer>();
            for (var child : batch.results()) {
                if (child == null) {
                    continue;
                }
                for (var entry : child.values().entrySet()) {
                    var previous = writers.putIfAbsent(entry.getKey(), child.index());
                    if (previous == null) {
                        writes.put(entry.getKey(), entry.getValue());
                    } else if (!Values.structurallyEqual(writes.get(entry.getKey()), entry.getValue())) {
                        var left = branches.get(previous);
                        var right = branches.get(child.index());
                        throw new ParallelOutputConflictException(entry.getKey(), previous, left.path(),
                                writes.get(entry.getKey()), child.index(), right.path(), entry.getValue());
                    }
                }
            }
            merged.commit(writes);
            var descriptor = instruction.options().get("out");
            if (descriptor != null) {
                target.commit(collectScopeOutputs(merged, descriptor));
            }
        }

        private void executeParallelLoop(Instruction instruction, Scope parent, Scope target, LoopSpec loop,
                                         ParallelRun run) {
            var outputNames = outputNames(instruction.options().get("out"), parent);
            var childInstruction = parallelLoopInstruction(instruction);
            var batch = runChildren(run, index -> {
                var itemScope = parent.fork();
                itemScope.set("item", loop.items().get(index));
                itemScope.set("itemIndex", index);
                itemScope.set("items", loop.items());
                return new ChildRequest(index, List.of(childInstruction), itemScope, null, index,
                        ChildCapture.OUTPUTS, outputNames);
            });
            publishChildHistories(batch);
            propagateChildTerminal(batch);
            observedFailure = null;

            var outputs = new LinkedHashMap<String, Object>();
            for (var name : outputNames) {
                var values = new ArrayList<Object>(Collections.nCopies(loop.items().size(), null));
                for (var child : batch.results()) {
                    values.set(child.index(), child.values().get(name));
                }
                outputs.put(name, Collections.unmodifiableList(values));
            }
            target.commit(outputs);
        }

        private static Instruction parallelLoopInstruction(Instruction instruction) {
            var options = new LinkedHashMap<>(instruction.options());
            options.remove("loop");
            var branches = new LinkedHashMap<>(instruction.branches());
            branches.remove("error");
            return new Instruction(-instruction.id() - 1, instruction.opcode(), instruction.sourceType(),
                    instruction.value(), options, branches, instruction.sourceRange(), instruction.path());
        }

        private final class ParallelRun {

            private final int count;
            private final int limit;
            private final ChildResult[] results;
            private List<TaskRuntime.HistoryEntry> historySnapshot;
            private volatile SchedulerMessage.Checkpoint checkpoint;
            private int next;

            private ParallelRun(int count, int limit) {
                this(count, limit, null);
            }

            private ParallelRun(int count, int limit, List<TaskRuntime.HistoryEntry> historySnapshot) {
                this.count = count;
                this.limit = limit;
                this.results = new ChildResult[count];
                this.historySnapshot = historySnapshot;
            }

            private boolean resume(String eventName, Map<String, Object> payload) {
                ChildResult match = null;
                for (var result : results) {
                    if (result == null || result.scheduler() == null
                            || !result.scheduler().waitsFor(eventName)) {
                        continue;
                    }
                    if (match != null) {
                        throw new IllegalStateException("Multiple live suspension waits use event '" + eventName + "'");
                    }
                    match = result;
                }
                if (match == null) {
                    return false;
                }
                match.scheduler().resume(eventName, payload);
                results[match.index()] = match.ready();
                return true;
            }

            private boolean waitsFor(String eventName) {
                for (var result : results) {
                    if (result != null && result.scheduler() != null
                            && result.scheduler().waitsFor(eventName)) {
                        return true;
                    }
                }
                return false;
            }

            private List<Suspension> waits() {
                var waits = new ArrayList<Suspension>();
                for (var result : results) {
                    if (result != null && result.scheduler() != null) {
                        waits.addAll(result.scheduler().waits());
                    }
                }
                return List.copyOf(waits);
            }

            private SchedulerMessage.Checkpoint checkpoint() {
                return checkpoint;
            }

            private void requestCheckpoint(SchedulerMessage.Checkpoint candidate) {
                if (checkpoint == null) {
                    checkpoint = candidate;
                }
            }
        }

        private ChildBatch runChildren(ParallelRun run, ChildFactory factory) {
            if (run.count == 0) {
                return new ChildBatch(run.results);
            }
            if (run.historySnapshot == null) {
                run.historySnapshot = taskRuntime.history();
            }
            var historySnapshot = run.historySnapshot;
            var completions = new LinkedBlockingQueue<ChildResult>();
            var handles = new ChildHandle[run.count];
            var active = 0;
            var stopAdmission = false;
            var cancelling = false;
            var cancellationDeadline = 0L;
            while (true) {
                for (var index = 0; !stopAdmission && active < run.limit && index < run.next; index++) {
                    var previous = run.results[index];
                    if (previous == null || previous.message() != null || handles[index] != null) {
                        continue;
                    }
                    var handle = new ChildHandle(previous.request(), completions);
                    handles[index] = handle;
                    handle.submit(() -> runChild(previous.request(), previous.scheduler(), historySnapshot,
                            previous.history(), run));
                    run.results[index] = previous.running();
                    active++;
                }
                while (!stopAdmission && active < run.limit && run.next < run.count) {
                    var request = factory.create(run.next);
                    var handle = new ChildHandle(request, completions);
                    handles[run.next] = handle;
                    handle.submit(() -> runChild(request, null, historySnapshot, List.of(), run));
                    run.results[run.next] = ChildResult.running(request);
                    run.next++;
                    active++;
                }
                if (active == 0) {
                    break;
                }

                ChildResult completed;
                try {
                    if (cancelling) {
                        var remaining = cancellationDeadline - System.nanoTime();
                        completed = remaining > 0
                                ? completions.poll(remaining, TimeUnit.NANOSECONDS)
                                : null;
                        if (completed == null) {
                            throw new ParallelShutdownException(cancellationGrace);
                        }
                    } else {
                        completed = completions.take();
                    }
                } catch (InterruptedException e) {
                    cancelChildren(handles);
                    var terminated = drainCancelledChildren(completions, active);
                    Thread.currentThread().interrupt();
                    if (!terminated) {
                        throw new ParallelShutdownException(cancellationGrace);
                    }
                    throw CancelSignal.INSTANCE;
                }
                if (completed.message() instanceof SchedulerMessage.Failed failed) {
                    observedFailure = failed.context();
                }
                run.results[completed.index()] = completed;
                handles[completed.index()] = null;
                active--;
                if (!stopAdmission && completed.message() instanceof SchedulerMessage.Checkpoint) {
                    stopAdmission = true;
                } else if (!cancelling && stopsAdmission(completed.message())) {
                    stopAdmission = true;
                    cancelling = true;
                    cancellationDeadline = System.nanoTime() + cancellationGrace.toNanos();
                    cancelChildren(handles);
                }
            }
            run.checkpoint = null;
            return new ChildBatch(run.results);
        }

        private ChildResult runChild(ChildRequest request, Scheduler existing,
                                     List<TaskRuntime.HistoryEntry> historySnapshot,
                                     List<TaskRuntime.HistoryEntry> previousHistory, ParallelRun parentParallel) {
            var scheduler = existing != null ? existing : new Scheduler(plan, request.instructions(), request.scope());
            scheduler.parentParallel = parentParallel;
            scheduler.inheritedLoopItemIndex = request.loopItemIndex();
            scheduler.flowDepthOffset = flowDepthOffset + fiber.flowDepth();
            scheduler.callback = callback;
            try {
                var isolated = taskRuntime.withIsolatedHistory(historySnapshot,
                        () -> drive(scheduler));
                return childResult(request, scheduler, isolated.value(), isolated.history());
            } catch (Throwable e) {
                var instruction = request.instructions().getFirst();
                var context = new FailureContext(instruction, e, List.of(request.scope().flowName()),
                        request.branchIndex(), request.loopItemIndex(), null);
                return new ChildResult(request.index(), Map.of(), new SchedulerMessage.Failed(context),
                        List.of(), null, null);
            }
        }

        private ChildResult childResult(ChildRequest request, Scheduler scheduler, SchedulerMessage message,
                                        List<TaskRuntime.HistoryEntry> history) {
            if (message instanceof SchedulerMessage.Failed failed) {
                var current = failed.context();
                var callStack = parentCallStack();
                callStack.addAll(current.callStack());
                var context = new FailureContext(current.instruction(), current.cause(), callStack,
                        request.branchIndex(), request.loopItemIndex() != null
                        ? request.loopItemIndex() : current.loopItemIndex(), current.retryAttempt());
                message = new SchedulerMessage.Failed(context);
            }
            var values = message instanceof SchedulerMessage.Completed ? capture(request) : Map.<String, Object>of();
            var resumable = message instanceof SchedulerMessage.Suspended
                    || message instanceof SchedulerMessage.Checkpoint;
            return new ChildResult(request.index(), values, message, history,
                    resumable ? request : null, resumable ? scheduler : null);
        }

        private Map<String, Object> capture(ChildRequest request) {
            if (request.capture() == ChildCapture.OVERLAY) {
                return Values.map(request.scope().localValues());
            }
            var result = new LinkedHashMap<String, Object>();
            for (var name : request.outputNames()) {
                var value = request.scope().lookup(name);
                if (value.present()) {
                    result.put(name, value.value());
                }
            }
            return Values.map(result);
        }
        private void publishChildHistories(ChildBatch batch) {
            for (var i = 0; i < batch.results().length; i++) {
                var child = batch.results()[i];
                if (child == null || child.history().isEmpty()) {
                    continue;
                }
                taskRuntime.appendHistory(child.history());
                batch.results()[i] = child.withoutHistory();
            }
        }
        private void propagateChildTerminal(ChildBatch batch) {
            for (var child : batch.results()) {
                if (child != null && child.message() instanceof SchedulerMessage.Exited) {
                    throw ExitSignal.INSTANCE;
                }
            }
            for (var child : batch.results()) {
                if (child != null && child.message() instanceof SchedulerMessage.Returned) {
                    throw ReturnSignal.INSTANCE;
                }
            }
            var failures = new ArrayList<FailureContext>();
            var eventOwners = new LinkedHashMap<String, Suspension>();
            var formOwners = new LinkedHashMap<String, Suspension>();
            Suspension suspension = null;
            var cancelled = false;
            for (var child : batch.results()) {
                if (child == null) {
                    continue;
                }
                if (child.scheduler() != null) {
                    for (var wait : child.scheduler().waits()) {
                        var previous = eventOwners.putIfAbsent(wait.eventName(), wait);
                        if (previous != null) {
                            throw new DuplicateSuspensionEventException(wait.eventName(),
                                    previous.path(), wait.path());
                        }
                        if (wait.payload().get("formName") instanceof String formName) {
                            previous = formOwners.putIfAbsent(formName, wait);
                            if (previous != null) {
                                throw new DuplicateFormException(formName, previous.path(), wait.path());
                            }
                        }
                    }
                }
                if (child.message() instanceof SchedulerMessage.Failed failed) {
                    failures.add(failed.context());
                } else if (child.message() instanceof SchedulerMessage.Suspended suspended
                        && suspension == null) {
                    suspension = suspended.suspension();
                } else if (child.message() instanceof SchedulerMessage.Cancelled) {
                    cancelled = true;
                }
            }
            var realFailure = failures.stream()
                    .filter(context -> !(context.cause() instanceof InvocationExecutor.ShutdownException)
                            && !(context.cause() instanceof ParallelShutdownException))
                    .findFirst()
                    .orElse(null);
            if (realFailure != null) {
                failures.stream()
                        .map(FailureContext::cause)
                        .filter(cause -> cause != realFailure.cause())
                        .forEach(realFailure.cause()::addSuppressed);
                throw new ChildFailureSignal(realFailure);
            }
            if (!failures.isEmpty()) {
                var shutdown = failures.getFirst();
                for (var i = 1; i < failures.size(); i++) {
                    var secondary = failures.get(i).cause();
                    if (secondary != shutdown.cause()) {
                        shutdown.cause().addSuppressed(secondary);
                    }
                }
                throw new ChildFailureSignal(shutdown);
            }
            if (cancelled) {
                throw CancelSignal.INSTANCE;
            }
            SchedulerMessage.Checkpoint checkpoint = null;
            for (var child : batch.results()) {
                if (child == null || !(child.message() instanceof SchedulerMessage.Checkpoint candidate)) {
                    continue;
                }
                if (checkpoint != null && (!checkpoint.name().equals(candidate.name())
                        || !Values.structurallyEqual(checkpoint.metadata(), candidate.metadata()))) {
                    throw new CheckpointException("Parallel branches reached conflicting checkpoints '"
                            + checkpoint.name() + "' and '" + candidate.name() + "'", null);
                }
                checkpoint = candidate;
                batch.results()[child.index()] = child.ready();
            }
            if (checkpoint != null) {
                throw new CheckpointSignal(checkpoint.name(), checkpoint.metadata(), checkpoint.instruction());
            }
            if (suspension != null) {
                throw new SuspendSignal(suspension, root);
            }
        }
        private boolean stopsAdmission(SchedulerMessage message) {
            return message instanceof SchedulerMessage.Failed
                    || message instanceof SchedulerMessage.Returned
                    || message instanceof SchedulerMessage.Exited
                    || message instanceof SchedulerMessage.Cancelled;
        }

        private void cancelChildren(ChildHandle[] handles) {
            for (var handle : handles) {
                if (handle != null) {
                    handle.cancel();
                }
            }
        }

        private boolean drainCancelledChildren(LinkedBlockingQueue<ChildResult> completions, int active) {
            var deadline = System.nanoTime() + cancellationGrace.toNanos();
            while (active > 0) {
                var remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    if (completions.poll(remaining, TimeUnit.NANOSECONDS) == null) {
                        return false;
                    }
                    active--;
                } catch (InterruptedException ignored) {
                }
            }
            return true;
        }

        private LoopSpec loop(Instruction instruction, Scope scope) {
            var raw = instruction.options().get("loop");
            if (raw == null) {
                return null;
            }
            if (!(raw instanceof Map<?, ?> options)) {
                throw new IllegalArgumentException("loop must be a mapping");
            }
            var mode = expressions.evaluate(options.containsKey("mode") ? options.get("mode") : "serial",
                    scope, String.class);
            if (!Set.of("serial", "parallel").contains(mode)) {
                throw new IllegalArgumentException("loop.mode must resolve to 'serial' or 'parallel'");
            }
            var value = expressions.evaluate(options.get("items"), scope);
            var items = new ArrayList<Object>();
            if (value instanceof Map<?, ?> map) {
                map.forEach((key, item) -> {
                    var entry = new LinkedHashMap<String, Object>();
                    entry.put("key", key);
                    entry.put("value", item);
                    items.add(Collections.unmodifiableMap(entry));
                });
            } else if (value instanceof Collection<?> collection) {
                items.addAll(collection);
            } else if (value != null && value.getClass().isArray()) {
                for (var i = 0; i < Array.getLength(value); i++) {
                    items.add(Array.get(value, i));
                }
            } else {
                throw new IllegalArgumentException("loop.items must resolve to a collection, array, or mapping");
            }
            var parallel = "parallel".equals(mode);
            var configuredParallelism = options.containsKey("parallelism")
                    ? options.get("parallelism")
                    : plan.configuration().values().getOrDefault("parallelLoopParallelism",
                    Runtime.getRuntime().availableProcessors());
            var parallelism = parallel
                    ? (int) Math.min(workerParallelism,
                    positiveWholeNumber(configuredParallelism, scope, "loop.parallelism", 1_000_000))
                    : 1;
            return new LoopSpec(Collections.unmodifiableList(items), parallel, parallelism);
        }

        private RetrySpec retry(Instruction instruction, Scope scope) {
            var raw = instruction.options().get("retry");
            if (raw == null) {
                return null;
            }
            Object times = raw;
            Object delay = 5;
            Object input = null;
            if (raw instanceof Map<?, ?> options) {
                times = options.containsKey("times") ? options.get("times") : 1;
                delay = options.containsKey("delay") ? options.get("delay") : 5;
                input = options.get("in");
            }
            var retries = nonNegativeWholeNumber(times, scope, "retry.times", Integer.MAX_VALUE);
            var seconds = nonNegativeWholeNumber(delay, scope, "retry.delay", RetryScheduler.MAX_DELAY_SECONDS);
            return new RetrySpec((int) retries, Duration.ofSeconds(seconds), input);
        }

        private long nonNegativeWholeNumber(Object raw, Scope scope, String name, long maximum) {
            var value = expressions.evaluate(raw, scope);
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException(name + " must resolve to a number");
            }
            var result = number.longValue();
            if (result < 0 || Double.compare(number.doubleValue(), result) != 0 || result > maximum) {
                throw new IllegalArgumentException(name + " must be a whole number from 0 to " + maximum);
            }
            return result;
        }
        private long positiveWholeNumber(Object raw, Scope scope, String name, long maximum) {
            var result = nonNegativeWholeNumber(raw, scope, name, maximum);
            if (result == 0) {
                throw new IllegalArgumentException(name + " must be greater than zero");
            }
            return result;
        }

        private Map<String, Object> userLocals(Scope source) {
            var result = new LinkedHashMap<>(source.localValues());
            result.remove(RETRY_ATTEMPT);
            return result;
        }

        private Map<String, Object> declaredOutputs(Scope source, Object rawDescriptor, Scope descriptorScope) {
            var result = new LinkedHashMap<String, Object>();
            for (var name : outputNames(rawDescriptor, descriptorScope)) {
                var value = source.lookup(name);
                if (value.present()) {
                    result.put(name, value.value());
                }
            }
            return result;
        }

        private List<String> outputNames(Object rawDescriptor, Scope scope) {
            var descriptor = rawDescriptor instanceof String text && text.contains("${")
                    ? expressions.evaluate(text, scope)
                    : rawDescriptor;
            if (descriptor == null) {
                return List.of();
            }
            if (descriptor instanceof String name) {
                return List.of(name);
            }
            if (descriptor instanceof Map<?, ?> mappings) {
                return mappings.keySet().stream().map(Object::toString).toList();
            }
            if (descriptor instanceof Iterable<?> names) {
                var result = new ArrayList<String>();
                names.forEach(name -> result.add(name.toString()));
                return result;
            }
            throw new IllegalArgumentException("out must resolve to a variable name, list, or mapping");
        }

        private FailureContext failureContext(Instruction instruction, Throwable cause) {
            if (cause instanceof ChildFailureSignal childFailure) {
                return childFailure.context;
            }
            var callStack = new ArrayList<String>();
            Integer loopItemIndex = null;
            Integer retryAttempt = null;
            for (var frame : fiberFrames()) {
                if (frame instanceof SequenceFrame sequence && sequence.flow()) {
                    callStack.add(sequence.scope().flowName());
                } else if (frame instanceof StepFrame step) {
                    if (loopItemIndex == null && step.loop != null) {
                        loopItemIndex = step.itemIndex;
                    }
                    if (retryAttempt == null && step.retry != null) {
                        retryAttempt = step.attempt;
                    }
                }
            }
            Collections.reverse(callStack);
            return new FailureContext(instruction, cause, callStack, null, loopItemIndex, retryAttempt);
        }

        private Iterable<FrameNode> fiberFrames() {
            return fiber.continuation;
        }

        private ArrayList<String> parentCallStack() {
            var callStack = new ArrayList<String>();
            for (var frame : fiberFrames()) {
                if (frame instanceof SequenceFrame sequence && sequence.flow()) {
                    callStack.add(sequence.scope().flowName());
                }
            }
            Collections.reverse(callStack);
            return callStack;
        }

        private Map<String, Object> lastError(Throwable error, Instruction instruction) {
            var context = failureContext(instruction, error);
            var actual = context.cause();
            var source = context.instruction();
            var result = new LinkedHashMap<String, Object>();
            result.put("message", safeMessage(actual));
            result.put("type", actual instanceof RestoredFailure restored
                    ? restored.originalType() : actual.getClass().getName());
            result.put("source", source.sourceRange().source());
            result.put("line", source.sourceRange().line());
            result.put("column", source.sourceRange().column());
            result.put("path", source.path());
            result.put("callStack", context.callStack());
            if (context.parallelBranchIndex() != null) {
                result.put("parallelBranchIndex", context.parallelBranchIndex());
            }
            if (context.loopItemIndex() != null) {
                result.put("loopItemIndex", context.loopItemIndex());
            }
            if (context.retryAttempt() != null) {
                result.put("retryAttempt", context.retryAttempt());
            }
            if (actual instanceof UserDefinedException userError && userError.getPayload() != null) {
                result.put("payload", userError.getPayload());
            } else if (actual instanceof RestoredFailure restored) {
                result.put("payload", restored.payload());
            }
            return Collections.unmodifiableMap(result);
        }

        private enum Phase {
            NEW,
            WAITING,
            SUSPENDED,
            RESUMING,
            PARALLEL,
            HANDLING
        }
    }

    private boolean condition(Object value, String path) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean result) {
            return result;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text)) {
                return false;
            }
        }
        throw new IllegalArgumentException("Condition at " + path + " must resolve to true or false");
    }

    private void publishResult(Scope target, Object descriptor, Object result) {
        if (descriptor == null) {
            return;
        }
        var staged = new LinkedHashMap<String, Object>();
        if (descriptor instanceof String name) {
            staged.put(name, result);
        } else if (descriptor instanceof Iterable<?> names) {
            if (!(result instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException("List out requires a task result mapping");
            }
            names.forEach(name -> {
                if (name != null && values.containsKey(name.toString())) {
                    staged.put(name.toString(), values.get(name.toString()));
                }
            });
        } else if (descriptor instanceof Map<?, ?> mappings) {
            var resultScope = target.child(target.flowName());
            resultScope.set("result", result);
            mappings.forEach((key, expression) -> staged.put(key.toString(),
                    expressions.evaluate(expression, resultScope)));
        } else {
            throw new IllegalArgumentException("Expression out must be a variable name, list, or mapping");
        }
        target.commit(staged);
    }

    private void publishScopeOutputs(Scope target, Scope source, Object rawDescriptor) {
        var descriptor = rawDescriptor instanceof String text && text.contains("${")
                ? expressions.evaluate(text, target)
                : rawDescriptor;
        target.commit(collectScopeOutputs(source, descriptor));
    }

    private Map<String, Object> collectScopeOutputs(Scope source, Object descriptor) {
        var staged = new LinkedHashMap<String, Object>();
        if (descriptor instanceof String name) {
            copy(source, staged, name);
        } else if (descriptor instanceof Iterable<?> names) {
            names.forEach(name -> copy(source, staged, name.toString()));
        } else if (descriptor instanceof Map<?, ?> mappings) {
            mappings.forEach((name, expression) -> staged.put(name.toString(),
                    expressions.evaluate(expression, source)));
        } else {
            throw new IllegalArgumentException("out must resolve to a variable name, list, or mapping");
        }
        return staged;
    }

    private void copy(Scope source, Map<String, Object> target, String name) {
        var value = lookupPath(source, name);
        if (value.present()) {
            target.put(name, value.value());
        }
    }

    private Scope.Lookup lookupPath(Scope source, String name) {
        var direct = source.lookup(name);
        if (direct.present() || name.indexOf('.') < 0) {
            return direct;
        }
        var parts = name.split("\\.");
        var root = source.lookup(parts[0]);
        if (!root.present()) {
            return root;
        }
        Object current = root.value();
        for (var i = 1; i < parts.length; i++) {
            if (current instanceof Map<?, ?> map && map.containsKey(parts[i])) {
                current = map.get(parts[i]);
            } else if (current instanceof List<?> list) {
                try {
                    current = list.get(Integer.parseInt(parts[i]));
                } catch (RuntimeException e) {
                    return new Scope.Lookup(false, null);
                }
            } else {
                return new Scope.Lookup(false, null);
            }
        }
        return new Scope.Lookup(true, current);
    }

    private Map<String, Object> processOutputs(ExecutionPlan plan, Scope root) {
        var descriptor = plan.configuration().values().get("out");
        return descriptor == null ? Map.of() : collectScopeOutputs(root, descriptor);
    }

    private ProcessResult.Failure failure(FailureContext context) {
        var instruction = context.instruction();
        var range = instruction.sourceRange();
        var code = context.cause() instanceof ParallelShutdownException
                || context.cause() instanceof InvocationExecutor.ShutdownException
                || context.cause() instanceof CheckpointException
                ? "V25_ENGINE"
                : context.cause() instanceof ParallelOutputConflictException
                ? "PARALLEL_OUTPUT_CONFLICT"
                : context.cause() instanceof DuplicateSuspensionEventException
                ? "DUPLICATE_SUSPENSION_EVENT"
                : "V25_STEP_FAILED";
        return new ProcessResult.Failure(code, safeMessage(context.cause()), range.source(), range.line(),
                range.column(), instruction.path(), context.callStack(), context.parallelBranchIndex(),
                context.loopItemIndex(), context.retryAttempt(), context.cause());
    }

    private ProcessResult.Failure runtimeFailure(Throwable cause) {
        return new ProcessResult.Failure("V25_RUNTIME", safeMessage(cause), null, 0, 0, "$", List.of(), null,
                null, null, cause);
    }

    private String safeMessage(Throwable error) {
        var candidate = error;
        while (candidate.getCause() != null && candidate.getCause() != candidate) {
            candidate = candidate.getCause();
        }
        var message = candidate.getMessage();
        return message == null || message.isBlank() ? candidate.getClass().getSimpleName() : message;
    }

    private sealed interface SchedulerMessage permits SchedulerMessage.Completed, SchedulerMessage.Failed,
            SchedulerMessage.Suspended, SchedulerMessage.Cancelled, SchedulerMessage.Waiting,
            SchedulerMessage.Checkpoint, SchedulerMessage.Returned, SchedulerMessage.Exited {

        record Completed() implements SchedulerMessage {
        }

        record Failed(FailureContext context) implements SchedulerMessage {
        }

        record Suspended(Suspension suspension) implements SchedulerMessage {
        }

        record Cancelled(FailureContext context) implements SchedulerMessage {

            private Cancelled() {
                this(null);
            }
        }

        record Waiting(CompletableFuture<Void> timer, FailureContext context) implements SchedulerMessage {
        }
        record Checkpoint(String name, Map<String, Object> metadata, Instruction instruction)
                implements SchedulerMessage {
            public Checkpoint {
                metadata = Values.map(metadata);
            }
        }


        record Returned() implements SchedulerMessage {
        }

        record Exited() implements SchedulerMessage {
        }
    }

    private record FailureContext(Instruction instruction, Throwable cause, List<String> callStack,
                                  Integer parallelBranchIndex, Integer loopItemIndex, Integer retryAttempt) {

        private FailureContext {
            callStack = List.copyOf(callStack);
        }
    }

    @FunctionalInterface
    private interface ChildFactory {
        ChildRequest create(int index);
    }

    @FunctionalInterface
    private interface ChildAction {
        ChildResult run();
    }
    private enum ChildCapture {
        OVERLAY,
        OUTPUTS
    }

    private record ChildRequest(int index, List<Instruction> instructions, Scope scope,
                                Integer branchIndex, Integer loopItemIndex, ChildCapture capture,
                                List<String> outputNames) {
        private ChildRequest {
            instructions = List.copyOf(instructions);
            outputNames = List.copyOf(outputNames);
        }

    }

    private record ChildResult(int index, Map<String, Object> values, SchedulerMessage message,
                               List<TaskRuntime.HistoryEntry> history, ChildRequest request,
                               Scheduler scheduler) {
        private ChildResult {
            values = Values.map(values);
            history = List.copyOf(history);
        }

        private static ChildResult running(ChildRequest request) {
            return new ChildResult(request.index(), Map.of(), null, List.of(), request, null);
        }

        private ChildResult running() {
            return new ChildResult(index, Map.of(), null, history, request, scheduler);
        }

        private ChildResult ready() {
            return new ChildResult(index, Map.of(), null, history, request, scheduler);
        }

        private ChildResult withoutHistory() {
            return new ChildResult(index, values, message, List.of(), request, scheduler);
        }
    }

    private record ChildBatch(ChildResult[] results) {
    }

    private static final class ChildHandle {

        private final ChildRequest request;
        private final LinkedBlockingQueue<ChildResult> completions;

        private Future<?> future;
        private boolean started;
        private boolean cancelled;
        private boolean finished;

        private ChildHandle(ChildRequest request, LinkedBlockingQueue<ChildResult> completions) {
            this.request = request;
            this.completions = completions;
        }

        private synchronized void submit(ChildAction action) {
            future = InvocationExecutor.submitCurrent(() -> {
                if (!begin()) {
                    return null;
                }
                finish(action.run());
                return null;
            });
        }

        private synchronized boolean begin() {
            if (cancelled) {
                finish(cancelledResult());
                return false;
            }
            started = true;
            return true;
        }

        private synchronized void cancel() {
            if (finished) {
                return;
            }
            cancelled = true;
            if (future != null) {
                future.cancel(true);
            }
            if (!started) {
                finish(cancelledResult());
            }
        }

        private synchronized void finish(ChildResult result) {
            if (finished) {
                return;
            }
            if (cancelled) {
                result = cancelledResult();
            }
            finished = true;
            completions.add(result);
        }

        private ChildResult cancelledResult() {
            return new ChildResult(request.index(), Map.of(), new SchedulerMessage.Cancelled(), List.of(),
                    null, null);
        }
    }

    private static final class ChildFailureSignal extends RuntimeException {

        private final FailureContext context;

        private ChildFailureSignal(FailureContext context) {
            super(null, context.cause(), false, false);
            this.context = context;
        }
    }

    private static final class CheckpointException extends RuntimeException {

        private CheckpointException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class RestoredFailure extends RuntimeException {

        private final String originalType;
        private final Map<String, Object> payload;

        private RestoredFailure(String originalType, String message, Map<String, Object> payload) {
            super(message);
            this.originalType = originalType;
            this.payload = Values.map(payload);
        }

        private String originalType() {
            return originalType;
        }

        private Map<String, Object> payload() {
            return payload;
        }
    }

    private static final class ParallelOutputConflictException extends RuntimeException {

        private ParallelOutputConflictException(String key, int leftIndex, String leftPath, Object leftValue,
                                                int rightIndex, String rightPath, Object rightValue) {
            super("Variable '" + key + "' has conflicting writes from parallel branch " + leftIndex + " ("
                    + leftPath + ", " + valueType(leftValue) + ") and branch " + rightIndex + " ("
                    + rightPath + ", " + valueType(rightValue) + ")");
        }

        private static String valueType(Object value) {
            return value == null ? "null" : value.getClass().getName();
        }
    }
    private static final class ParallelShutdownException extends RuntimeException {

        private ParallelShutdownException(Duration grace) {
            super("Parallel children did not terminate within cancellation grace " + grace);
        }
    }


    private interface FrameNode {
    }

    private static final class Fiber {

        private final Deque<FrameNode> continuation = new ArrayDeque<>();

        private void push(FrameNode frame) {
            continuation.push(frame);
        }

        private FrameNode current() {
            return continuation.element();
        }

        private FrameNode pop() {
            return continuation.pop();
        }

        private boolean done() {
            return continuation.isEmpty();
        }

        private void clear() {
            continuation.clear();
        }

        private int flowDepth() {
            var result = 0;
            for (var frame : continuation) {
                if (frame instanceof SequenceFrame sequence && sequence.flow()) {
                    result++;
                }
            }
            return result;
        }
    }

    private static final class DuplicateSuspensionEventException extends RuntimeException {

        private DuplicateSuspensionEventException(String eventName, String leftPath, String rightPath) {
            super("Suspension event '" + eventName + "' is used by multiple live branches ("
                    + leftPath + " and " + rightPath + ")");
        }
    }

    private static final class DuplicateFormException extends RuntimeException {

        private DuplicateFormException(String formName, String leftPath, String rightPath) {
            super("Form '" + formName + "' is used by multiple live parallel branches ("
                    + leftPath + " and " + rightPath + ")");
        }
    }
    private static final class SequenceFrame implements FrameNode {

        private final List<Instruction> instructions;
        private final Scope scope;
        private final Scope outputTarget;
        private final Object outputDescriptor;
        private final boolean flow;
        private final Instruction outputInstruction;

        private int programCounter;

        private SequenceFrame(List<Instruction> instructions, Scope scope, Scope outputTarget,
                              Object outputDescriptor, boolean flow, Instruction outputInstruction) {
            this.instructions = instructions;
            this.scope = scope;
            this.outputTarget = outputTarget;
            this.outputDescriptor = outputDescriptor;
            this.flow = flow;
            this.outputInstruction = outputInstruction;
        }

        private static SequenceFrame flow(List<Instruction> instructions, Scope scope, Scope outputTarget,
                                          Object outputDescriptor) {
            return flow(instructions, scope, outputTarget, outputDescriptor, null);
        }

        private static SequenceFrame flow(List<Instruction> instructions, Scope scope, Scope outputTarget,
                                          Object outputDescriptor, Instruction outputInstruction) {
            return new SequenceFrame(instructions, scope, outputTarget, outputDescriptor, true, outputInstruction);
        }

        private static SequenceFrame sequence(List<Instruction> instructions, Scope scope, Scope outputTarget,
                                              Object outputDescriptor) {
            return sequence(instructions, scope, outputTarget, outputDescriptor, null);
        }

        private static SequenceFrame sequence(List<Instruction> instructions, Scope scope, Scope outputTarget,
                                              Object outputDescriptor, Instruction outputInstruction) {
            return new SequenceFrame(instructions, scope, outputTarget, outputDescriptor, false,
                    outputInstruction);
        }

        private boolean complete() {
            return programCounter >= instructions.size();
        }

        private Instruction next() {
            return instructions.get(programCounter++);
        }

        private Scope scope() {
            return scope;
        }

        private Scope outputTarget() {
            return outputTarget;
        }

        private Object outputDescriptor() {
            return outputDescriptor;
        }

        private Instruction outputInstruction() {
            return outputInstruction;
        }

        private boolean flow() {
            return flow;
        }
    }

    private record LoopSpec(List<Object> items, boolean parallel, int parallelism) {
    }

    private record RetrySpec(int times, Duration delay, Object input) {
    }

    private abstract static sealed class ControlSignal extends RuntimeException
            permits ExitSignal, ReturnSignal, SuspendSignal, CancelSignal, RetryWaitSignal, CheckpointSignal {
        private ControlSignal() {
            super(null, null, false, false);
        }
    }

    private static final class ExitSignal extends ControlSignal {

        private static final ExitSignal INSTANCE = new ExitSignal();
    }
    private static final class ReturnSignal extends ControlSignal {

        private static final ReturnSignal INSTANCE = new ReturnSignal();
    }

    private static final class CancelSignal extends ControlSignal {

        private static final CancelSignal INSTANCE = new CancelSignal();
    }

    private static final class RetryWaitSignal extends ControlSignal {

        private final CompletableFuture<Void> timer;
        private final FailureContext context;

        private RetryWaitSignal(CompletableFuture<Void> timer, FailureContext context) {
            this.timer = timer;
            this.context = context;
        }
    }

    private static final class CheckpointSignal extends ControlSignal {

        private final String name;
        private final Map<String, Object> metadata;
        private final Instruction instruction;

        private CheckpointSignal(String name, Map<String, Object> metadata, Instruction instruction) {
            this.name = name;
            this.metadata = metadata;
            this.instruction = instruction;
        }
    }

    private static final class SuspendSignal extends ControlSignal {

        private final Suspension suspension;
        private final Scope scope;

        private SuspendSignal(Suspension suspension, Scope scope) {
            this.suspension = suspension;
            this.scope = scope;
        }
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(failure);
    }
}
