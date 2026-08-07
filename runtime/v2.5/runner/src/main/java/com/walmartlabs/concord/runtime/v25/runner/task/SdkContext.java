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

import com.walmartlabs.concord.runtime.model.Location;
import com.walmartlabs.concord.runtime.v2.model.Flow;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinitionConfiguration;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.model.TaskCallOptions;
import com.walmartlabs.concord.runtime.v2.sdk.ApiConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.DockerService;
import com.walmartlabs.concord.runtime.v2.sdk.Execution;
import com.walmartlabs.concord.runtime.v2.sdk.FileService;
import com.walmartlabs.concord.runtime.v2.sdk.LockService;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.NestedFlowExecutor;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.runtime.v25.runner.engine.Suspension;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import com.walmartlabs.concord.runtime.v25.runner.plan.ExecutionPlan;
import com.walmartlabs.concord.runtime.v25.runner.plan.Instruction;
import com.walmartlabs.concord.runtime.v25.runner.scope.Scope;
import com.walmartlabs.concord.svm.EvalResult;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import com.walmartlabs.concord.svm.V25CompatibilityState;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

final class SdkContext implements Context {
    private static final State COMPATIBILITY_STATE = V25CompatibilityState.create();
    private final ExpressionService expressions;
    private final Scope scope;
    private final Variables variables;
    private final Variables defaultVariables;
    private final TaskEnvironment environment;
    private final ProcessConfiguration processConfiguration;
    private ProcessDefinition processDefinition;
    private final ExecutionPlan plan;
    private final Instruction instruction;
    private final String taskName;
    private final Consumer<Suspension> suspensionConsumer;
    private final NestedFlowExecutor nestedFlowExecutor;
    private com.walmartlabs.concord.svm.Runtime runtime;
    private final UUID correlationId;

    SdkContext(ExpressionService expressions, Scope scope, ExecutionPlan plan, Instruction instruction,
               String taskName, TaskEnvironment environment, NestedFlowExecutor nestedFlowExecutor,
               Consumer<Suspension> suspensionConsumer) {
        this(expressions, scope, plan, instruction, taskName, null, environment, nestedFlowExecutor,
                suspensionConsumer);
    }

    SdkContext(ExpressionService expressions, Scope scope, ExecutionPlan plan, Instruction instruction,
               String taskName, TaskRuntime.StepContext stepContext, TaskEnvironment environment,
               NestedFlowExecutor nestedFlowExecutor, Consumer<Suspension> suspensionConsumer) {
        this.expressions = expressions;
        this.scope = scope;
        this.variables = new ScopeVariables(scope);
        this.environment = environment;
        this.defaultVariables = new MapBackedVariables(environment.defaultTaskVariables()
                .getOrDefault(taskName, Map.of()));
        this.processConfiguration = processConfiguration(plan, scope, environment);
        this.plan = plan;
        this.instruction = instruction;
        this.taskName = taskName;
        this.correlationId = correlationId(stepContext != null ? stepContext.correlationId()
                        : plan.id() + ":" + instruction.id(),
                stepContext != null ? stepContext.metadata() : Map.of());
        this.nestedFlowExecutor = nestedFlowExecutor;
        this.suspensionConsumer = suspensionConsumer;
    }

    SdkContext(ExpressionService expressions, Scope scope, ExecutionPlan plan, Instruction instruction,
               String taskName, TaskEnvironment environment, Consumer<Suspension> suspensionConsumer) {
        this(expressions, scope, plan, instruction, taskName, environment,
                (flowName, input) -> {
                    throw new UnsupportedOperationException("Nested flow execution is not supported by scripts");
                },
                suspensionConsumer);
    }

    @Override
    public java.nio.file.Path workingDirectory() {
        return environment.workingDirectory();
    }

    @Override
    public UUID processInstanceId() {
        return environment.processInstanceId();
    }

    @Override
    public Variables variables() {
        return variables;
    }

    @Override
    public Variables defaultVariables() {
        return defaultVariables;
    }

    @Override
    public NestedFlowExecutor nestedFlowExecutor() {
        return nestedFlowExecutor;
    }

    @Override
    public FileService fileService() {
        return environment.fileService();
    }

    @Override
    public DockerService dockerService() {
        return required(environment.dockerService(), DockerService.class);
    }

    @Override
    public SecretService secretService() {
        return required(environment.secretService(), SecretService.class);
    }

    @Override
    public LockService lockService() {
        return required(environment.lockService(), LockService.class);
    }

    @Override
    public ApiConfiguration apiConfiguration() {
        return required(environment.apiConfiguration(), ApiConfiguration.class);
    }

    @Override
    public ProcessConfiguration processConfiguration() {
        return processConfiguration;
    }

    @Override
    public Execution execution() {
        return new Execution() {
            @Override
            public ThreadId currentThreadId() {
                return SdkContext.this.state().getRootThreadId();
            }

            @Override
            public com.walmartlabs.concord.svm.Runtime runtime() {
                return SdkContext.this.runtime();
            }

            @Override
            public State state() {
                return SdkContext.this.state();
            }

            @Override
            public ProcessDefinition processDefinition() {
                return compatibilityProcessDefinition();
            }

            @Override
            public Step currentStep() {
                var options = TaskCallOptions.builder();
                var meta = instruction.options().get("meta");
                if (meta instanceof Map<?, ?> values) {
                    values.forEach((key, value) -> {
                        if (value instanceof Serializable serializable) {
                            options.putMeta(String.valueOf(key), serializable);
                        }
                    });
                }
                var range = instruction.sourceRange();
                var location = Location.builder()
                        .fileName(range.source())
                        .lineNum(range.line())
                        .column(range.column())
                        .build();
                return new TaskCall(location, taskName, options.build());
            }

            @Override
            public String currentFlowName() {
                return scope.flowName();
            }

            @Override
            public UUID correlationId() {
                return correlationId;
            }
        };
    }

    @Override
    public Compiler compiler() {
        return required(environment.services().get(Compiler.class), Compiler.class);
    }

    @Override
    public <T> T eval(Object value, Class<T> expectedType) {
        return expressions.evaluate(value, scope, expectedType);
    }

    @Override
    public <T> T eval(Object value, Map<String, Object> overrides, Class<T> expectedType) {
        var child = scope.child(scope.flowName());
        child.commit(overrides != null ? overrides : Map.of());
        return expressions.evaluate(value, child, expectedType);
    }

    @Override
    public void suspend(String eventName) {
        suspensionConsumer.accept(suspension(eventName, false, Map.of()));
    }

    @Override
    public void reentrantSuspend(String eventName, Map<String, Serializable> payload) {
        var copy = new LinkedHashMap<String, Object>();
        if (payload != null) {
            copy.putAll(payload);
        }
        suspensionConsumer.accept(suspension(eventName, true, copy));
    }

    private Suspension suspension(String eventName, boolean reentrant, Map<String, Object> payload) {
        return new Suspension(eventName, reentrant, taskName, payload, instruction.id(), instruction.path());
    }

    static UUID correlationId(String value, Map<String, Object> metadata) {
        if (value == null) {
            return null;
        }
        var route = value + ":" + metadata.getOrDefault("loopItemIndex", 0) + ":"
                + metadata.getOrDefault("retryAttempt", 0);
        try {
            return UUID.fromString(route);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(route.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static ProcessConfiguration processConfiguration(ExecutionPlan plan, Scope scope,
                                                             TaskEnvironment environment) {
        if (environment.processConfiguration() != null) {
            return environment.processConfiguration();
        }
        var configuration = plan.configuration().values();
        return ProcessConfiguration.builder()
                .instanceId(environment.processInstanceId())
                .debug(environment.debug())
                .dryRun(environment.dryRun())
                .entryPoint(plan.configuration().entryPoint())
                .arguments(map(configuration.get("arguments")))
                .meta(map(configuration.get("meta")))
                .defaultTaskVariables(environment.defaultTaskVariables())
                .out(strings(configuration.get("out")))
                .build();
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static List<String> strings(Object value) {
        if (value instanceof String string) {
            return List.of(string);
        }
        if (!(value instanceof Iterable<?> values)) {
            return List.of();
        }
        var result = new ArrayList<String>();
        values.forEach(item -> result.add(String.valueOf(item)));
        return result;
    }

    private static State state() {
        return COMPATIBILITY_STATE;
    }

    private com.walmartlabs.concord.svm.Runtime runtime() {
        if (runtime == null) {
            runtime = new CompatibilityRuntime();
        }
        return runtime;
    }

    private ProcessDefinition compatibilityProcessDefinition() {
        if (processDefinition != null) {
            return processDefinition;
        }
        var location = Location.builder().build();
        var flows = new LinkedHashMap<String, Flow>();
        plan.flows().keySet().forEach(name -> flows.put(name, Flow.of(location, List.of())));
        var configuration = ProcessDefinitionConfiguration.builder()
                .runtime("concord-v2.5")
                .entryPoint(plan.configuration().entryPoint())
                .arguments(plan.configuration().arguments())
                .build();
        processDefinition = ProcessDefinition.builder()
                .configuration(configuration)
                .flows(flows)
                .publicFlows(plan.publicFlows())
                .build();
        return processDefinition;
    }

    private final class CompatibilityRuntime implements com.walmartlabs.concord.svm.Runtime {

        @Override
        public void spawn(State state, ThreadId threadId) {
            throw unsupported("Runtime.spawn");
        }

        @Override
        public EvalResult eval(State state, ThreadId threadId) throws Exception {
            throw unsupported("Runtime.eval");
        }

        @Override
        public <T> T getService(Class<T> type) {
            if (type == ProcessDefinition.class) {
                return type.cast(compatibilityProcessDefinition());
            }
            return required(environment.services().get(type), type);
        }
    }

    private static <T> T required(Object value, Class<T> type) {
        if (value == null) {
            throw unsupported(type.getSimpleName());
        }
        return type.cast(value);
    }

    private static UnsupportedOperationException unsupported(String service) {
        return new UnsupportedOperationException(service + " is not configured for this runtime-v2.5 process");
    }

    private record ScopeVariables(Scope scope) implements Variables {

        @Override
        public Object get(String key) {
            return scope.get(key);
        }

        @Override
        public void set(String key, Object value) {
            scope.set(key, value);
        }

        @Override
        public boolean has(String key) {
            return scope.contains(key);
        }

        @Override
        public Map<String, Object> toMap() {
            return scope.snapshot();
        }
    }
}
