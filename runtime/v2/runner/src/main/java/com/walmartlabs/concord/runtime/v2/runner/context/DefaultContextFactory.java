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

import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import javax.inject.Inject;
import java.util.UUID;

public class DefaultContextFactory implements ContextFactory {

    private final WorkingDirectory workingDirectory;
    private final InstanceId processInstanceId;
    private final ProcessConfiguration processConfiguration;

    @Inject
    public DefaultContextFactory(WorkingDirectory workingDirectory, InstanceId processInstanceId, ProcessConfiguration processConfiguration) {
        this.workingDirectory = workingDirectory;
        this.processInstanceId = processInstanceId;
        this.processConfiguration = processConfiguration;
    }

    @Override
    public Context create(Runtime runtime, State state, ThreadId currentThreadId, Step currentStep) {
        return create(runtime, state, currentThreadId, currentStep, null);
    }

    @Override
    public Context create(Runtime runtime, State state, ThreadId currentThreadId, Step currentStep, UUID correlationId) {
        ProcessDefinition pd = runtime.getService(ProcessDefinition.class);

        Compiler compiler = runtime.getService(Compiler.class);
        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);

        return new ContextImpl(compiler, ee, currentThreadId, runtime, state, pd, currentStep, correlationId, workingDirectory.getValue(), processInstanceId.getValue(), processConfiguration.projectInfo());
    }
}
