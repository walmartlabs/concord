package com.walmartlabs.concord.runtime.v2.runner.guice;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.runtime.v2.runner.*;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointService;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointUploader;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.DefaultCheckpointService;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.DefaultCheckpointUploader;
import com.walmartlabs.concord.runtime.v2.runner.logging.DefaultLoggingClient;
import com.walmartlabs.concord.runtime.v2.runner.logging.LoggerProvider;
import com.walmartlabs.concord.runtime.v2.runner.logging.LoggingClient;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.runtime.v2.runner.remote.ApiClientProvider;
import com.walmartlabs.concord.runtime.v2.runner.remote.DefaultProcessStatusCallback;
import com.walmartlabs.concord.runtime.v2.runner.remote.EventRecordingExecutionListener;
import com.walmartlabs.concord.runtime.v2.runner.remote.TaskCallEventRecordingListener;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.svm.ExecutionListener;

import javax.inject.Singleton;

/**
 * Default set of services.
 */
public class DefaultRunnerModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new BaseRunnerModule());

        // singletons
        bind(CheckpointUploader.class).to(DefaultCheckpointUploader.class).in(Singleton.class);
        bind(CheckpointService.class).to(DefaultCheckpointService.class).in(Singleton.class);
        bind(DependencyManager.class).to(DefaultDependencyManager.class).in(Singleton.class);
        bind(DockerService.class).to(DefaultDockerService.class).in(Singleton.class);
        bind(FileService.class).to(DefaultFileService.class).in(Singleton.class);
        bind(ProcessEventWriter.class).to(DefaultProcessEventWriter.class).in(Singleton.class);
        bind(EventReportingService.class).to(DefaultEventReportingService.class).in(Singleton.class);
        bind(LockService.class).to(DefaultLockService.class).in(Singleton.class);
        bind(PersistenceService.class).to(DefaultPersistenceService.class).in(Singleton.class);
        bind(ProcessStatusCallback.class).to(DefaultProcessStatusCallback.class).in(Singleton.class);
        bind(SecretService.class).to(DefaultSecretService.class).in(Singleton.class);
        bind(RunnerLogger.class).toProvider(LoggerProvider.class);
        bind(LoggingClient.class).to(DefaultLoggingClient.class);

        bind(ApiClient.class).toProvider(ApiClientProvider.class);
        bind(DefaultTaskVariablesService.class).toProvider(DefaultTaskVariablesProvider.class);

        Multibinder<TaskCallListener> taskCallListeners = Multibinder.newSetBinder(binder(), TaskCallListener.class);
        taskCallListeners.addBinding().to(TaskCallEventRecordingListener.class);

        Multibinder<ExecutionListener> executionListeners = Multibinder.newSetBinder(binder(), ExecutionListener.class);
        executionListeners.addBinding().to(EventRecordingExecutionListener.class);
        executionListeners.addBinding().to(EventReportingService.class);
        executionListeners.addBinding().to(MetadataProcessor.class);
        executionListeners.addBinding().to(OutVariablesProcessor.class);
        executionListeners.addBinding().to(SensitiveDataPersistenceService.class);
        executionListeners.addBinding().to(StackTraceCollector.class);
    }
}
