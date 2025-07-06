package com.walmartlabs.concord.runtime.v2.runner.context;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.ProcessDefinitionUtils;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.runner.vm.SuspendCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.TaskSuspendCommand;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class ContextImpl implements Context {

    private final Compiler compiler;
    private final ExpressionEvaluator expressionEvaluator;
    private final ThreadId currentThreadId;
    private final Runtime runtime;
    private final State state;
    private final ProcessDefinition processDefinition;
    private final Step currentStep;
    private final UUID correlationId;
    private final Path workingDir;
    private final UUID processInstanceId;
    private final Variables variables;
    private final FileService fileService;
    private final DockerService dockerService;
    private final SecretService secretService;
    private final LockService lockService;
    private final ApiConfiguration apiConfiguration;
    private final ProcessConfiguration processConfiguration;

    public ContextImpl(Compiler compiler,
                       ExpressionEvaluator expressionEvaluator,
                       ThreadId currentThreadId,
                       Runtime runtime,
                       State state,
                       ProcessDefinition processDefinition,
                       Step currentStep,
                       UUID correlationId,
                       Path workingDir,
                       UUID processInstanceId,
                       FileService fileService,
                       DockerService dockerService,
                       SecretService secretService,
                       LockService lockService,
                       ApiConfiguration apiConfiguration,
                       ProcessConfiguration processConfiguration) {

        this.compiler = compiler;
        this.expressionEvaluator = expressionEvaluator;
        this.currentThreadId = currentThreadId;
        this.runtime = runtime;
        this.state = state;
        this.processDefinition = processDefinition;
        this.currentStep = currentStep;
        this.correlationId = correlationId;
        this.workingDir = workingDir;
        this.processInstanceId = processInstanceId;
        this.variables = new ContextVariables(this);
        this.fileService = fileService;
        this.dockerService = dockerService;
        this.secretService = secretService;
        this.lockService = lockService;
        this.apiConfiguration = apiConfiguration;
        this.processConfiguration = processConfiguration;
    }

    @Override
    public Path workingDirectory() {
        return workingDir;
    }

    @Override
    public UUID processInstanceId() {
        return processInstanceId;
    }

    @Override
    public Variables variables() {
        return variables;
    }

    @Override
    public Variables defaultVariables() {
        return new MapBackedVariables(Collections.emptyMap());
    }

    @Override
    public FileService fileService() {
        return fileService;
    }

    @Override
    public DockerService dockerService() {
        return dockerService;
    }

    @Override
    public SecretService secretService() {
        return secretService;
    }

    @Override
    public LockService lockService() {
        return lockService;
    }

    @Override
    public ApiConfiguration apiConfiguration() {
        return apiConfiguration;
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
                return currentThreadId;
            }

            @Override
            public Runtime runtime() {
                return runtime;
            }

            @Override
            public State state() {
                return state;
            }

            @Override
            public ProcessDefinition processDefinition() {
                return processDefinition;
            }

            @Override
            public Step currentStep() {
                return currentStep;
            }

            @Override
            public String currentFlowName() {
                return ProcessDefinitionUtils.getCurrentFlowName(processDefinition, currentStep);
            }

            @Override
            public UUID correlationId() {
                return correlationId;
            }
        };
    }

    @Override
    public Compiler compiler() {
        return compiler;
    }

    @Override
    public <T> T eval(Object v, Class<T> type) {
        return expressionEvaluator.eval(runtime.getService(EvalContextFactory.class).global(this), v, type);
    }

    @Override
    public <T> T eval(Object v, Map<String, Object> additionalVariables, Class<T> type) {
        return expressionEvaluator.eval(runtime.getService(EvalContextFactory.class).global(this, additionalVariables), v, type);
    }

    @Override
    public void suspend(String eventName) {
        state.peekFrame(currentThreadId).push(new SuspendCommand(eventName));
    }

    @Override
    public void reentrantSuspend(String eventName, Map<String, Serializable> taskState) {
        Step step = execution().currentStep();
        if (!(step instanceof TaskCall)) {
            throw new IllegalStateException("Calling 'suspendResume' is allowed only in task calls. Current step: " + (step != null ? step.getClass() : "n/a"));
        }

        state.peekFrame(currentThreadId)
                .push(new TaskSuspendCommand(correlationId, eventName, (TaskCall) step, taskState));
    }
}
