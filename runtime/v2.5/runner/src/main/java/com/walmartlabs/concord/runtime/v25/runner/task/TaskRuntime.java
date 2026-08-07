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

import com.sun.el.util.ReflectionUtil;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.CustomTaskMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.DryRunReady;
import com.walmartlabs.concord.runtime.v2.sdk.InvocationContext;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.NestedFlowExecutor;
import com.walmartlabs.concord.runtime.v2.sdk.ReentrantTask;
import com.walmartlabs.concord.runtime.v2.sdk.ResumeEvent;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveData;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.runtime.v25.model.Values;
import com.walmartlabs.concord.runtime.v25.model.SourceRange;
import com.walmartlabs.concord.runtime.v25.model.Definition25;
import com.walmartlabs.concord.runtime.v25.runner.engine.Suspension;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import com.walmartlabs.concord.runtime.v25.runner.plan.ExecutionPlan;
import com.walmartlabs.concord.runtime.v25.runner.plan.Instruction;
import com.walmartlabs.concord.runtime.v25.runner.plan.Opcode;
import com.walmartlabs.concord.runtime.v25.runner.scope.Scope;

import javax.el.MethodNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public final class TaskRuntime implements ExpressionService.TaskMethods {

    private final TaskRegistry registry;
    private final TaskEnvironment environment;
    private final ScriptRuntime scripts;
    private final Validator validator;
    private final List<TaskHook> hooks;
    private final List<HistoryEntry> history = new ArrayList<>();
    private final ThreadLocal<List<HistoryEntry>> localHistory = new ThreadLocal<>();
    private final ThreadLocal<NestedFlowExecutor> localNestedFlowExecutor = new ThreadLocal<>();
    private final ThreadLocal<Instruction> localCurrentInstruction = new ThreadLocal<>();
    private final ThreadLocal<StepContext> localStepContext = new ThreadLocal<>();
    private final AtomicReference<ExpressionService> expressions = new AtomicReference<>();

    public TaskRuntime(TaskRegistry registry, TaskEnvironment environment) {
        this(registry, environment, Validator.NONE, List.of());
    }

    public TaskRuntime(TaskRegistry registry, TaskEnvironment environment, Validator validator,
                       Collection<? extends TaskHook> hooks) {
        this.registry = registry;
        this.environment = environment;
        this.scripts = new ScriptRuntime(registry, environment);
        this.validator = validator;
        var ordered = new ArrayList<>(hooks);
        ordered.sort((left, right) -> {
            var order = Integer.compare(left.order(), right.order());
            return order != 0 ? order : left.getClass().getName().compareTo(right.getClass().getName());
        });
        this.hooks = List.copyOf(ordered);
    }

    public void bind(ExpressionService expressionService) {
        if (!expressions.compareAndSet(null, expressionService) && expressions.get() != expressionService) {
            throw new IllegalStateException("Task runtime is already bound to another expression service");
        }
        var holder = environment.services().get(SensitiveDataHolder.class);
        if (holder instanceof SensitiveDataHolder sensitiveData) {
            expressionService.bindSensitiveData(sensitiveData);
        }
    }

    public Outcome invokeScript(ExecutionPlan plan, Instruction instruction, Object rawOverrides, Scope scope) {
        return scripts.execute(expressions(), plan, instruction, rawOverrides, scope, currentNestedFlowExecutor(),
                localStepContext.get());
    }

    public Outcome invoke(ExecutionPlan plan, Instruction instruction, String taskName, Object rawInput,
                          Object rawOverrides, Scope scope) {
        var expressionService = expressions();
        var input = new LinkedHashMap<String, Object>();
        input.putAll(environment.defaultTaskVariables().getOrDefault(taskName, Map.of()));
        evaluateInput(expressionService, rawInput, scope, input);
        evaluateInput(expressionService, rawOverrides, scope, input);
        var suspension = new AtomicReference<Suspension>();
        var context = new SdkContext(expressionService, scope, plan, instruction, taskName, localStepContext.get(), environment,
                currentNestedFlowExecutor(), request -> {
                    if (!suspension.compareAndSet(null, request)) {
                        throw new IllegalStateException("Task requested more than one suspension");
                    }
                });
        var resolved = registry.resolve(context, taskName);
        var immutableInput = Values.map(input);
        var taskClass = resolved.taskClass();
        validator.validateInput(taskName, taskClass, immutableInput, validationMode(plan, "in"));

        var rawResult = invokeInWorker(() -> invokeTask(context, taskName, resolved.task(), taskClass, immutableInput,
                dryRunReady(expressionService, instruction, scope)));
        var outcome = normalize(taskName, instruction, rawResult, suspension.get());
        if (outcome.failure() == null && outcome.suspension() == null) {
            validateOutput(taskName, taskClass, outcome.values(), validationMode(plan, "out"));
        }
        currentHistory().add(new HistoryEntry(taskName, outcome.values(), outcome.successful()));
        return outcome;
    }
    public Outcome resume(ExecutionPlan plan, Instruction instruction, Suspension pending,
                          Map<String, Object> resumePayload, Scope scope) {
        if (!pending.reentrant() || pending.taskName() == null) {
            scope.commit(resumePayload);
            return new Outcome(Map.of(), null, null);
        }
        scope.commit(resumePayload);
        var taskName = pending.taskName();
        var requested = new AtomicReference<Suspension>();
        var context = new SdkContext(expressions(), scope, plan, instruction, taskName, localStepContext.get(), environment,
                currentNestedFlowExecutor(), suspension -> {
                    if (!requested.compareAndSet(null, suspension)) {
                        throw new IllegalStateException("Task requested more than one suspension");
                    }
                });
        var resolved = registry.resolve(context, taskName);
        var taskClass = resolved.taskClass();
        var state = new LinkedHashMap<String, Serializable>();
        pending.payload().forEach((key, value) -> {
            if (value != null && !(value instanceof Serializable)) {
                throw new IllegalArgumentException("Reentrant task state '" + key + "' is not serializable");
            }
            state.put(key, (Serializable) value);
        });
        var event = new ResumeEvent25(pending.eventName(), state);
        var rawResult = invokeInWorker(() -> invokeResume(context, taskName, resolved.task(), event));
        var outcome = normalize(taskName, instruction, rawResult, requested.get());
        if (outcome.failure() == null && outcome.suspension() == null) {
            validateOutput(taskName, taskClass, outcome.values(), validationMode(plan, "out"));
        }
        currentHistory().add(new HistoryEntry(taskName, outcome.values(), outcome.successful()));
        return outcome;
    }

    @SuppressWarnings("unchecked")
    private static void evaluateInput(ExpressionService expressions, Object rawInput, Scope scope,
                                      Map<String, Object> target) {
        var evaluated = expressions.evaluate(rawInput, scope);
        if (evaluated == null) {
            return;
        }
        if (!(evaluated instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("Task 'in' must evaluate to a mapping, got: "
                    + evaluated.getClass().getName());
        }
        var merged = Definition25.deepMerge(target, (Map<String, Object>) values);
        target.clear();
        target.putAll(merged);
    }
    private static ValidationMode validationMode(ExecutionPlan plan, String section) {
        Object current = plan.configuration().values().get("validation");
        if (current instanceof Map<?, ?> validation) {
            current = validation.get("taskCalls");
        }
        if (current instanceof Map<?, ?> taskCalls) {
            current = taskCalls.get(section);
        } else {
            current = null;
        }
        if (current == null) {
            return ValidationMode.DISABLED;
        }
        try {
            return ValidationMode.valueOf(current.toString().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported taskCalls." + section
                    + " validation mode: " + current, e);
        }
    }

    private void validateOutput(String taskName, Class<? extends Task> taskClass, Map<String, Object> output,
                                ValidationMode mode) {
        if (!output.containsKey("threadId")) {
            validator.validateOutput(taskName, taskClass, output, mode);
            return;
        }
        var taskOutput = new LinkedHashMap<>(output);
        taskOutput.remove("threadId");
        validator.validateOutput(taskName, taskClass, Values.map(taskOutput), mode);
    }

    @Override
    public boolean hasTask(String name) {
        return registry.hasTask(name);
    }

    @Override
    public Object invoke(String taskName, String methodName, Object[] arguments, Scope scope) {
        var expressionService = expressions();
        var instruction = currentInstruction();
        if (instruction == null) {
            instruction = new Instruction(-1, Opcode.EXPR, null, null, Map.of(), Map.of(),
                    new SourceRange(null, 0, 0, 0, 0), "expression.task." + taskName + "." + methodName);
        }
        var suspension = new AtomicReference<Suspension>();
        var context = new SdkContext(expressionService, scope, scope.plan(), instruction,
                taskName, localStepContext.get(), environment, currentNestedFlowExecutor(), request -> {
                    if (!suspension.compareAndSet(null, request)) {
                        throw new IllegalStateException("Task requested more than one suspension");
                    }
                });
        var result = invokeInWorker(() -> invokeMethod(context, taskName, methodName, arguments));
        if (suspension.get() != null) {
            throw new TaskSuspensionException(suspension.get(), scope);
        }
        return result;
    }

    @Override
    public ExpressionService.TaskMethods.BeanInvocation resolveBean(Object base, String methodName,
                                                                      Object[] arguments, Scope scope) {
        if (methodName == null) {
            return null;
        }
        var invocation = environment.beanMethodResolvers().stream()
                .map(resolver -> resolver.resolve(base, methodName, null, arguments))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        return invocation != null
                ? new ExpressionService.TaskMethods.BeanInvocation(invocation.invoke(invocationContext()))
                : null;
    }

    public List<HistoryEntry> history() {
        return List.copyOf(currentHistory());
    }

    public void replaceHistory(List<HistoryEntry> entries) {
        if (localHistory.get() != null) {
            throw new IllegalStateException("Cannot replace task history while executing an isolated child");
        }
        history.clear();
        history.addAll(entries);
    }

    public boolean dryRun() {
        return environment.dryRun();
    }

    public <T> T withNestedFlowExecutor(NestedFlowExecutor executor, Callable<T> action) {
        var previous = localNestedFlowExecutor.get();
        localNestedFlowExecutor.set(executor);
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (previous == null) {
                localNestedFlowExecutor.remove();
            } else {
                localNestedFlowExecutor.set(previous);
            }
        }
    }

    public <T> T withCurrentInstruction(Instruction instruction, Callable<T> action) {
        var previous = localCurrentInstruction.get();
        localCurrentInstruction.set(instruction);
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (previous == null) {
                localCurrentInstruction.remove();
            } else {
                localCurrentInstruction.set(previous);
            }
        }
    }

    public <T> HistoryResult<T> withIsolatedHistory(List<HistoryEntry> snapshot, Callable<T> action) {
        var previous = localHistory.get();
        var isolated = new ArrayList<>(snapshot);
        localHistory.set(isolated);
        try {
            var value = action.call();
            return new HistoryResult<>(value, List.copyOf(isolated.subList(snapshot.size(), isolated.size())));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (previous == null) {
                localHistory.remove();
            } else {
                localHistory.set(previous);
            }
        }
    }

    public void appendHistory(List<HistoryEntry> entries) {
        currentHistory().addAll(entries);
    }


    private NestedFlowExecutor currentNestedFlowExecutor() {
        var result = localNestedFlowExecutor.get();
        if (result != null) {
            return result;
        }
        return (flowName, input) -> {
            throw new UnsupportedOperationException("Nested flow execution is not supported by this runtime");
        };
    }

    public Instruction currentInstruction() {
        return localCurrentInstruction.get();
    }

    public <T> T withInvocationContext(StepContext context, Callable<T> action) {
        var previous = localStepContext.get();
        localStepContext.set(context);
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (previous == null) {
                localStepContext.remove();
            } else {
                localStepContext.set(previous);
            }
        }
    }

    private <T> T invokeInWorker(Callable<T> action) {
        var history = localHistory.get();
        var nestedFlowExecutor = localNestedFlowExecutor.get();
        var currentInstruction = localCurrentInstruction.get();
        var stepContext = localStepContext.get();
        return InvocationExecutor.callCurrent(
                () -> withWorkerContext(history, nestedFlowExecutor, currentInstruction, stepContext, action));
    }

    private <T> T withWorkerContext(List<HistoryEntry> history, NestedFlowExecutor nestedFlowExecutor,
                                    Instruction currentInstruction, StepContext stepContext, Callable<T> action)
            throws Exception {
        var previousHistory = localHistory.get();
        var previousNestedFlowExecutor = localNestedFlowExecutor.get();
        var previousCurrentInstruction = localCurrentInstruction.get();
        var previousStepContext = localStepContext.get();
        setWorkerContext(history, nestedFlowExecutor, currentInstruction, stepContext);
        try {
            return action.call();
        } finally {
            setWorkerContext(previousHistory, previousNestedFlowExecutor, previousCurrentInstruction,
                    previousStepContext);
        }
    }

    private void setWorkerContext(List<HistoryEntry> history, NestedFlowExecutor nestedFlowExecutor,
                                  Instruction currentInstruction, StepContext stepContext) {
        if (history == null) {
            localHistory.remove();
        } else {
            localHistory.set(history);
        }
        if (nestedFlowExecutor == null) {
            localNestedFlowExecutor.remove();
        } else {
            localNestedFlowExecutor.set(nestedFlowExecutor);
        }
        if (currentInstruction == null) {
            localCurrentInstruction.remove();
        } else {
            localCurrentInstruction.set(currentInstruction);
        }
        if (stepContext == null) {
            localStepContext.remove();
        } else {
            localStepContext.set(stepContext);
        }
    }
    private List<HistoryEntry> currentHistory() {
        var result = localHistory.get();
        return result != null ? result : history;
    }

    private static boolean dryRunReady(ExpressionService expressions, Instruction instruction, Scope scope) {
        var meta = instruction.options().get("meta");
        var value = meta instanceof Map<?, ?> values ? values.get("dryRunReady") : null;
        return Boolean.TRUE.equals(expressions.evaluate(value, scope));
    }

    private TaskResult invokeTask(SdkContext context, String taskName, Task task, Class<? extends Task> taskClass,
                                  Map<String, Object> input, boolean stepDryRunReady) throws Exception {
        if (environment.dryRun() && !stepDryRunReady && task.getClass().getAnnotation(DryRunReady.class) == null) {
            throw new UserDefinedException("Dry-run mode is not supported for '" + taskName + "' task");
        }
        var inputVariables = new MapBackedVariables(input);
        var invocation = new Invocation(taskName, "execute", List.of(input), currentHistory(), localStepContext.get());
        return invoke(context, taskName, taskClass, "execute", List.of(inputVariables), invocation, () -> {
            try {
                var result = task.execute(inputVariables);
                registerSensitiveTaskResult(task, result);
                return result;
            } catch (Exception e) {
                return TaskResult.fail(e);
            }
        });
    }

    private TaskResult invokeResume(SdkContext context, String taskName, Task task, ResumeEvent event) throws Exception {
        if (!(task instanceof ReentrantTask reentrant)) {
            throw new IllegalStateException("Task '" + taskName + "' does not implement "
                    + ReentrantTask.class.getSimpleName());
        }
        var invocation = new Invocation(taskName, "resume", List.of(event), currentHistory(), localStepContext.get());
        return invoke(context, taskName, task.getClass(), "resume", List.of(event), invocation, () -> {
            try {
                return reentrant.resume(event);
            } catch (Exception e) {
                return TaskResult.fail(e);
            }
        });
    }

    private Object invokeMethod(SdkContext context, String taskName, String methodName, Object[] arguments)
            throws Exception {
        var resolved = registry.resolve(context, taskName);
        var task = resolved.task();
        if (environment.dryRun() && task.getClass().getAnnotation(DryRunReady.class) == null) {
            throw new UserDefinedException("Dry-run mode is not supported for '" + taskName + "' task");
        }
        var custom = environment.taskMethodResolvers().stream()
                .map(resolver -> resolver.resolve(task, methodName, null, arguments))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        var taskClass = custom != null ? custom.taskClass() : resolved.taskClass();
        var method = custom == null ? findTaskMethod(taskClass, methodName, arguments) : null;
        if (custom == null && (method == null || !Modifier.isPublic(method.getModifiers()))) {
            throw new MethodNotFoundException("No public task method '" + taskName + "." + methodName + "'");
        }
        var converted = method != null ? convertArguments(method, arguments) : arguments;
        if (method != null) {
            registerSensitiveArguments(method, converted);
        }
        var originalArguments = new ArrayList<Object>(Arrays.asList(arguments));
        var invocation = new Invocation(taskName, methodName, originalArguments, currentHistory(),
                localStepContext.get());
        return invoke(context, taskName, taskClass, methodName, originalArguments, invocation, () -> {
            var result = custom != null
                    ? custom.invoke(invocationContext())
                    : invokeReflectively(method, task, converted);
            if (method != null) {
                registerSensitiveResult(method, result);
            }
            return result;
        });
    }

    private static Method findTaskMethod(Class<? extends Task> taskClass, String methodName, Object[] arguments) {
        try {
            return ReflectionUtil.findMethod(taskClass, methodName, null, arguments);
        } catch (MethodNotFoundException e) {
            // ReflectionUtil matches by arity and rejects varargs calls that miss fixed
            // parameters. Fall back to a name-only lookup so convertArguments can report
            // a precise arity error; keep the original failure for ambiguous overloads.
            var candidates = java.util.Arrays.stream(taskClass.getMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .toList();
            if (candidates.size() == 1) {
                return candidates.getFirst();
            }
            throw e;
        }
    }

    private Object[] convertArguments(Method method, Object[] arguments) {
        var types = method.getParameterTypes();        if (!method.isVarArgs()) {
            if (arguments.length != types.length) {
                throw new IllegalArgumentException("Task method '" + method.getName() + "' expects "
                        + types.length + " argument(s), got " + arguments.length);
            }
            var result = new Object[arguments.length];
            for (var i = 0; i < result.length; i++) {
                result[i] = expressions().coerce(arguments[i], types[i]);
            }
            return result;
        }
        var fixed = types.length - 1;
        if (arguments.length < fixed) {
            throw new IllegalArgumentException("Task method '" + method.getName() + "' expects at least "
                    + fixed + " argument(s), got " + arguments.length);
        }
        var result = new Object[types.length];
        for (var i = 0; i < fixed; i++) {
            result[i] = expressions().coerce(arguments[i], types[i]);
        }
        var varargsType = types[fixed];
        if (arguments.length == types.length && arguments[fixed] != null
                && varargsType.isInstance(arguments[fixed])) {
            result[fixed] = arguments[fixed];
            return result;
        }
        var values = java.lang.reflect.Array.newInstance(varargsType.getComponentType(), arguments.length - fixed);
        for (var i = fixed; i < arguments.length; i++) {
            java.lang.reflect.Array.set(values, i - fixed,
                    expressions().coerce(arguments[i], varargsType.getComponentType()));
        }
        result[fixed] = values;
        return result;
    }

    private void registerSensitiveArguments(Method method, Object[] arguments) {
        var annotations = method.getParameterAnnotations();
        for (var i = 0; i < annotations.length; i++) {
            var sensitive = Arrays.stream(annotations[i])
                    .filter(SensitiveData.class::isInstance)
                    .map(SensitiveData.class::cast)
                    .findFirst()
                    .orElse(null);
            if (sensitive != null) {
                registerSensitive(arguments[i], sensitive);
            }
        }
    }

    private void registerSensitiveResult(Method method, Object result) {
        var sensitive = method.getAnnotation(SensitiveData.class);
        if (sensitive != null) {
            registerSensitive(result, sensitive);
        }
    }

    private void registerSensitive(Object value, SensitiveData annotation) {
        var holder = environment.services().get(SensitiveDataHolder.class);
        if (!(holder instanceof SensitiveDataHolder sensitiveDataHolder)) {
            return;
        }
        var values = new ArrayList<String>();
        if (annotation.keys().length > 0 && value instanceof Map<?, ?> map) {
            for (var key : annotation.keys()) {
                collectSensitive(valueAtPath(map, key), annotation.includeNestedValues(), values);
            }
        } else {
            collectSensitive(value, annotation.includeNestedValues(), values);
        }
        sensitiveDataHolder.addAll(values);
    }

    private static Object valueAtPath(Map<?, ?> source, String path) {
        Object value = source;
        for (var segment : path.split("\\.")) {
            if (value instanceof Map<?, ?> map) {
                value = map.get(segment);
            } else if (value instanceof List<?> list) {
                try {
                    value = list.get(Integer.parseInt(segment));
                } catch (RuntimeException e) {
                    return null;
                }
            } else {
                return null;
            }
            if (value == null) {
                return null;
            }
        }
        return value;
    }

    private static void collectSensitive(Object value, boolean nested, List<String> target) {
        if (value instanceof String string) {
            target.add(string);
        } else if (nested && value instanceof Map<?, ?> map) {
            map.values().forEach(item -> collectSensitive(item, true, target));
        } else if (nested && value instanceof Iterable<?> values) {
            values.forEach(item -> collectSensitive(item, true, target));
        } else if (nested && value != null && value.getClass().isArray()) {
            for (var i = 0; i < java.lang.reflect.Array.getLength(value); i++) {
                collectSensitive(java.lang.reflect.Array.get(value, i), true, target);
            }
        }
    }

    private static Object invokeReflectively(Method method, Object target, Object[] arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    private <T> T invoke(SdkContext context, String taskName, Class<? extends Task> taskClass, String methodName,
                         List<Object> arguments, Invocation invocation, Callable<T> action) throws Exception {
        var notified = new ArrayList<TaskHook>(hooks.size());
        T result = null;
        Throwable failure = null;
        try {
            for (var hook : hooks) {
                notified.add(hook);
                hook.before(invocation);
            }
            var interceptor = environment.taskInterceptor();
            result = interceptor != null
                    ? interceptor.invoke(context, taskName, taskClass, methodName, arguments, action)
                    : action.call();
            return result;
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            for (var hook : notified) {
                hook.after(invocation, result, failure);
            }
        }
    }
    private InvocationContext invocationContext() {
        return () -> (base, methodName, paramTypes, params) -> {
            var method = ReflectionUtil.findMethod(base.getClass(), methodName, paramTypes, params);
            if (method == null || !Modifier.isPublic(method.getModifiers())) {
                throw new MethodNotFoundException("No public method '" + methodName + "'");
            }
            var converted = convertArguments(method, params);
            registerSensitiveArguments(method, converted);
            try {
                var result = invokeReflectively(method, base, converted);
                registerSensitiveResult(method, result);
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private void registerSensitiveTaskResult(Task task, TaskResult result) {
        if (result instanceof TaskResult.SimpleResult simple) {
            try {
                registerSensitiveResult(task.getClass().getMethod("execute", com.walmartlabs.concord.runtime.v2.sdk.Variables.class),
                        simple.values());
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Task has no execute(Variables) method: " + task.getClass().getName(), e);
            }
        }
    }
    private static Outcome normalize(String taskName, Instruction instruction, TaskResult result,
                                     Suspension requested) {
        if (result == null) {
            throw new IllegalStateException("Task '" + taskName + "' returned null at " + instruction.path());
        }
        if (result instanceof TaskResult.SuspendResult suspend) {
            var suspension = requested != null ? requested
                    : new Suspension(suspend.eventName(), false, taskName, Map.of(), instruction.id(),
                    instruction.path());
            return new Outcome(Map.of(), null, suspension);
        }
        if (result instanceof TaskResult.ReentrantSuspendResult suspend) {
            var payload = new LinkedHashMap<String, Object>();
            payload.putAll(suspend.payload());
            var suspension = requested != null ? requested
                    : new Suspension(suspend.eventName(), true, taskName, payload, instruction.id(),
                    instruction.path());
            return new Outcome(Map.of(), null, suspension);
        }
        if (!(result instanceof TaskResult.SimpleResult simple)) {
            throw new IllegalArgumentException("Unsupported task result type: " + result.getClass().getName());
        }
        var values = new LinkedHashMap<>(simple.values());
        values.put("ok", simple.ok());
        if (simple.error() != null) {
            values.put("error", simple.error());
        }
        values.put("threadId", 0L);
        var frozen = Values.map(values);
        Throwable failure = null;
        if (!simple.ok()) {
            if (simple instanceof TaskResult.SimpleFailResult failed && failed.cause() != null) {
                failure = failed.cause();
            } else {
                failure = new UserDefinedException(simple.error() != null
                        ? simple.error()
                        : "Task '" + taskName + "' failed", frozen);
            }
        }
        if (requested != null) {
            return new Outcome(frozen, failure, requested);
        }
        return new Outcome(frozen, failure, null);
    }

    private record ResumeEvent25(String eventName, Map<String, Serializable> state)
            implements ResumeEvent {

        private ResumeEvent25 {
            state = Collections.unmodifiableMap(new LinkedHashMap<>(state));
        }
    }

    private ExpressionService expressions() {
        var result = expressions.get();
        if (result == null) {
            throw new IllegalStateException("Task runtime is not bound to an expression service");
        }
        return result;
    }

    public record Outcome(Map<String, Object> values, Throwable failure, Suspension suspension) {
        public Outcome {
            values = Values.map(values);
        }

        public boolean successful() {
            return failure == null;
        }
    }

    public static final class TaskSuspensionException extends RuntimeException {

        private final Suspension suspension;
        private final Scope scope;

        public TaskSuspensionException(Suspension suspension, Scope scope) {
            this.suspension = suspension;
            this.scope = scope;
        }

        public Suspension suspension() {
            return suspension;
        }

        public Scope scope() {
            return scope;
        }
    }

    public record HistoryEntry(String taskName, Map<String, Object> result, boolean successful) {
        public HistoryEntry {
            result = Values.map(result);
        }
    }
    public record HistoryResult<T>(T value, List<HistoryEntry> history) {
        public HistoryResult {
            history = List.copyOf(history);
        }
    }

    public record Invocation(String taskName, String methodName, List<Object> arguments,
                             List<HistoryEntry> history, StepContext step) {

        public Invocation {
            arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
            history = List.copyOf(history);
        }
    }

    public record StepContext(String processDefinitionId, String correlationId, String source,
                              int line, int column, Map<String, Object> metadata, Long logSegment) {

        public StepContext {
            metadata = Values.map(metadata);
        }
    }

    public interface Validator {
        Validator NONE = new Validator() {
            @Override
            public void validateInput(String taskName, Map<String, Object> input) {
            }

            @Override
            public void validateOutput(String taskName, Map<String, Object> output) {
            }
        };

        default void validateInput(String taskName, Class<? extends Task> taskClass,
                                   Map<String, Object> input, ValidationMode mode) {
            validateInput(taskName, input);
        }

        default void validateOutput(String taskName, Class<? extends Task> taskClass,
                                   Map<String, Object> output, ValidationMode mode) {
            validateOutput(taskName, output);
        }

        void validateInput(String taskName, Map<String, Object> input);

        void validateOutput(String taskName, Map<String, Object> output);
    }

    public enum ValidationMode {
        DISABLED,
        WARN,
        FAIL
    }

    public interface TaskHook {
        default int order() {
            return 0;
        }

        default void before(Invocation invocation) {
        }

        default void after(Invocation invocation, Object result, Throwable failure) {
        }
    }

    @FunctionalInterface
    public interface TaskInterceptor {
        <T> T invoke(Context context, String taskName, Class<? extends Task> taskClass, String methodName,
                     List<Object> arguments, Callable<T> action) throws Exception;
    }

}
