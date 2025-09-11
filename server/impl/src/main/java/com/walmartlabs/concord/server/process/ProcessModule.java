package com.walmartlabs.concord.server.process;

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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.process.loader.DelegatingProjectLoader;
import com.walmartlabs.concord.process.loader.ProjectLoader;
import com.walmartlabs.concord.runtime.v1.ProjectLoaderV1;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.server.org.secret.GitAuthResource;
import com.walmartlabs.concord.server.process.checkpoint.ProcessCheckpointResource;
import com.walmartlabs.concord.server.process.checkpoint.ProcessCheckpointV2Resource;
import com.walmartlabs.concord.server.process.event.ProcessEventDao;
import com.walmartlabs.concord.server.process.event.ProcessEventManager;
import com.walmartlabs.concord.server.process.event.ProcessEventResource;
import com.walmartlabs.concord.server.process.form.FormModule;
import com.walmartlabs.concord.server.process.locks.ProcessLocksDao;
import com.walmartlabs.concord.server.process.locks.ProcessLocksResource;
import com.walmartlabs.concord.server.process.locks.ProcessLocksWatchdog;
import com.walmartlabs.concord.server.process.logs.ProcessLogAccessManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.pipelines.processors.ExclusiveGroupProcessor;
import com.walmartlabs.concord.server.process.pipelines.processors.InvalidProcessStateExceptionMapper;
import com.walmartlabs.concord.server.process.pipelines.processors.TemplateScriptProcessor;
import com.walmartlabs.concord.server.process.pipelines.processors.policy.*;
import com.walmartlabs.concord.server.process.queue.*;
import com.walmartlabs.concord.server.process.queue.dispatcher.ConcurrentProcessFilter;
import com.walmartlabs.concord.server.process.queue.dispatcher.Dispatcher;
import com.walmartlabs.concord.server.process.queue.dispatcher.ExclusiveProcessFilter;
import com.walmartlabs.concord.server.process.queue.dispatcher.Filter;
import com.walmartlabs.concord.server.process.state.ProcessCheckpointDao;
import com.walmartlabs.concord.server.process.state.ProcessCheckpointManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.process.waits.*;
import com.walmartlabs.concord.server.sdk.BackgroundTask;
import com.walmartlabs.concord.server.sdk.ProcessKeyCache;
import com.walmartlabs.concord.server.sdk.log.ProcessLogListener;
import com.walmartlabs.concord.server.sdk.process.CustomEnqueueProcessor;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.*;

public class ProcessModule implements Module {

    @Override
    public void configure(Binder binder) {
        binder.bind(ImportManager.class).toProvider(ImportManagerProvider.class);
        binder.bind(ProcessEventDao.class).in(SINGLETON);
        binder.bind(ProcessEventManager.class).in(SINGLETON);
        binder.bind(ProcessLocksDao.class).in(SINGLETON);
        binder.bind(ProcessLocksDao.class).in(SINGLETON);
        binder.bind(ProcessLogAccessManager.class).in(SINGLETON);
        binder.bind(ProcessLogManager.class).in(SINGLETON);
        binder.bind(ProcessSecurityContext.class).in(SINGLETON);
        binder.bind(ProcessCheckpointDao.class).in(SINGLETON);
        binder.bind(ProcessCheckpointManager.class).in(SINGLETON);
        binder.bind(ProcessStateManager.class).in(SINGLETON);
        binder.bind(ProcessWaitManager.class).in(SINGLETON);

        newSetBinder(binder, ProjectLoader.class).addBinding().to(ProjectLoaderV1.class);
        newSetBinder(binder, ProjectLoader.class).addBinding().to(ProjectLoaderV2.class);

        bindSingletonScheduledTask(binder, ProcessCleaner.class);
        bindSingletonScheduledTask(binder, ProcessLocksWatchdog.class);
        bindSingletonScheduledTask(binder, ProcessQueueWatchdog.class);
        bindSingletonScheduledTask(binder, ProcessWaitWatchdog.class);

        binder.bind(Dispatcher.class).in(SINGLETON);
        newSetBinder(binder, BackgroundTask.class).addBinding().to(Dispatcher.class);
        newSetBinder(binder, BackgroundTask.class).addBinding().toProvider(EnqueuedTaskProvider.class).in(SINGLETON);

        newSetBinder(binder, ProcessStatusListener.class).addBinding().to(WaitProcessStatusListener.class);
        newSetBinder(binder, ProcessStatusListener.class).addBinding().to(ExternalProcessListenerHandler.class);
        newSetBinder(binder, ProcessStatusListener.class).addBinding().to(WaitConditionUpdater.class);
        newSetBinder(binder, ProcessStatusListener.class).addBinding().to(TotalRuntimeCalculator.class);

        newSetBinder(binder, Filter.class).addBinding().to(ConcurrentProcessFilter.class);
        newSetBinder(binder, Filter.class).addBinding().to(ExclusiveProcessFilter.class);

        newSetBinder(binder, ProcessWaitHandler.class).addBinding().to(WaitProcessFinishHandler.class);
        newSetBinder(binder, ProcessWaitHandler.class).addBinding().to(WaitProcessLockHandler.class);
        newSetBinder(binder, ProcessWaitHandler.class).addBinding().to(WaitProcessSleepHandler.class);

        newSetBinder(binder, ProcessLogListener.class);

        newSetBinder(binder, PolicyApplier.class).addBinding().to(ContainerPolicyApplier.class);
        newSetBinder(binder, PolicyApplier.class).addBinding().to(FilePolicyApplier.class);
        newSetBinder(binder, PolicyApplier.class).addBinding().to(ProcessRuntimePolicyApplier.class);
        newSetBinder(binder, PolicyApplier.class).addBinding().to(ProcessTimeoutPolicyApplier.class);
        newSetBinder(binder, PolicyApplier.class).addBinding().to(WorkspacePolicyApplier.class);

        newSetBinder(binder, CustomEnqueueProcessor.class);

        bindJaxRsResource(binder, ProcessCheckpointResource.class);
        bindJaxRsResource(binder, ProcessCheckpointV2Resource.class);
        bindJaxRsResource(binder, ProcessEventResource.class);
        bindJaxRsResource(binder, ProcessHeartbeatResource.class);
        bindJaxRsResource(binder, ProcessKvResource.class);
        bindJaxRsResource(binder, ProcessLocksResource.class);
        bindJaxRsResource(binder, ProcessLogResourceV2.class);
        bindJaxRsResource(binder, ProcessResource.class);
        bindJaxRsResource(binder, ProcessResourceV2.class);

        bindExceptionMapper(binder, InvalidProcessStateExceptionMapper.class);
        bindExceptionMapper(binder, ProcessExceptionMapper.class);

        binder.bind(TemplateScriptProcessor.class).in(SINGLETON);
        binder.bind(ProcessKeyCache.class).to(com.walmartlabs.concord.server.process.queue.ProcessKeyCache.class).in(SINGLETON);

        binder.install(new ExclusiveGroupProcessor.ModeProcessorModule());
        binder.install(new FormModule());
        binder.install(new ProcessQueueGaugeModule());
        binder.install(new ProcessKeyCacheGaugeModule());
    }
}
