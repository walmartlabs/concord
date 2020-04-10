package com.walmartlabs.concord.runtime.v2.runner.guice;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.runtime.v2.runner.*;
import com.walmartlabs.concord.runtime.v2.runner.compiler.DefaultCompiler;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.context.DefaultContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.DefaultExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallPolicyChecker;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskResultListener;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskV2Provider;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.FileService;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;

/**
 * Contains basic services that can work in anyenvironment (unit tests, actual runtime, CLI, etc).
 */
public class BaseRunnerModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ContextFactory.class).to(DefaultContextFactory.class);
        bind(FileService.class).to(DefaultFileService.class);
        bind(Compiler.class).to(DefaultCompiler.class);
        bind(PolicyEngine.class).toProvider(PolicyEngineProvider.class);
        bind(SynchronizationService.class).to(DefaultSynchronizationService.class);
        bind(ExpressionEvaluator.class).to(DefaultExpressionEvaluator.class);
        bind(ScriptEvaluator.class).to(DefaultScriptEvaluator.class);
        bind(ResourceResolver.class).to(DefaultResourceResolver.class);
        bind(TaskResultService.class);

        Multibinder<TaskProvider> taskProviders = Multibinder.newSetBinder(binder(), TaskProvider.class);
        taskProviders.addBinding().to(TaskV2Provider.class);

        Multibinder<TaskCallListener> taskCallListeners = Multibinder.newSetBinder(binder(), TaskCallListener.class);
        taskCallListeners.addBinding().to(TaskCallPolicyChecker.class);
        taskCallListeners.addBinding().to(TaskResultListener.class);
    }
}
