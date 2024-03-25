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
import com.walmartlabs.concord.server.process.pipelines.processors.policy.*;
import com.walmartlabs.concord.server.process.queue.EnqueuedTaskProvider;
import com.walmartlabs.concord.server.process.queue.ExternalProcessListenerHandler;
import com.walmartlabs.concord.server.process.queue.ProcessQueueWatchdog;
import com.walmartlabs.concord.server.process.queue.ProcessStatusListener;
import com.walmartlabs.concord.server.process.queue.dispatcher.ConcurrentProcessFilter;
import com.walmartlabs.concord.server.process.queue.dispatcher.Dispatcher;
import com.walmartlabs.concord.server.process.queue.dispatcher.ExclusiveProcessFilter;
import com.walmartlabs.concord.server.process.queue.dispatcher.Filter;
import com.walmartlabs.concord.server.process.state.ProcessCheckpointDao;
import com.walmartlabs.concord.server.process.state.ProcessCheckpointManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.process.waits.*;
import com.walmartlabs.concord.server.sdk.BackgroundTask;
import com.walmartlabs.concord.server.sdk.log.ProcessLogListener;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;
import static com.walmartlabs.concord.server.Utils.bindSingletonScheduledTask;

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

        bindJaxRsResource(binder, ProcessCheckpointResource.class);
        bindJaxRsResource(binder, ProcessCheckpointV2Resource.class);
        bindJaxRsResource(binder, ProcessEventResource.class);
        bindJaxRsResource(binder, ProcessHeartbeatResource.class);
        bindJaxRsResource(binder, ProcessKvResource.class);
        bindJaxRsResource(binder, ProcessLocksResource.class);
        bindJaxRsResource(binder, ProcessLogResourceV2.class);
        bindJaxRsResource(binder, ProcessResource.class);
        bindJaxRsResource(binder, ProcessResourceV2.class);

        binder.install(new ExclusiveGroupProcessor.ModeProcessorModule());
        binder.install(new FormModule());
    }
}
