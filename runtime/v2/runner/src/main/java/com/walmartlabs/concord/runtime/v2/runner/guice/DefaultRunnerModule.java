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
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.runtime.v2.runner.*;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointService;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.DefaultCheckpointService;
import com.walmartlabs.concord.runtime.v2.runner.remote.ApiClientProvider;
import com.walmartlabs.concord.runtime.v2.runner.remote.DefaultProcessStatusCallback;
import com.walmartlabs.concord.runtime.v2.runner.remote.EventRecordingExecutionListener;
import com.walmartlabs.concord.runtime.v2.runner.remote.TaskCallEventRecordingListener;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.svm.ExecutionListener;

/**
 * Default set of services.
 */
public class DefaultRunnerModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new BaseRunnerModule());

        bind(ApiClient.class).toProvider(ApiClientProvider.class);
        bind(CheckpointService.class).to(DefaultCheckpointService.class);
        bind(DefaultTaskVariablesService.class).toProvider(DefaultTaskVariablesProvider.class);
        bind(DependencyManager.class).to(DefaultDependencyManager.class);
        bind(DockerService.class).to(DefaultDockerService.class);
        bind(FileService.class).to(DefaultFileService.class);
        bind(LockService.class).to(DefaultLockService.class);
        bind(PersistenceService.class).to(DefaultPersistenceService.class);
        bind(ProcessStatusCallback.class).to(DefaultProcessStatusCallback.class);
        bind(SecretService.class).to(DefaultSecretService.class);

        Multibinder<TaskCallListener> taskCallListeners = Multibinder.newSetBinder(binder(), TaskCallListener.class);
        taskCallListeners.addBinding().to(TaskCallEventRecordingListener.class);

        Multibinder<ExecutionListener> executionListeners = Multibinder.newSetBinder(binder(), ExecutionListener.class);
        executionListeners.addBinding().to(EventRecordingExecutionListener.class);
        executionListeners.addBinding().to(MetadataProcessor.class);
        executionListeners.addBinding().to(OutVariablesProcessor.class);
    }
}
