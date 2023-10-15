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
import com.walmartlabs.concord.server.process.locks.ProcessLocksWatchdog;
import com.walmartlabs.concord.server.process.queue.EnqueuedTaskProvider;
import com.walmartlabs.concord.server.process.queue.ProcessQueueWatchdog;
import com.walmartlabs.concord.server.process.queue.dispatcher.Dispatcher;
import com.walmartlabs.concord.server.process.waits.ProcessWaitWatchdog;
import com.walmartlabs.concord.server.sdk.BackgroundTask;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindSingletonScheduledTask;

public class ProcessModule implements Module {

    @Override
    public void configure(Binder binder) {
        bindSingletonScheduledTask(binder, ProcessCleaner.class);
        bindSingletonScheduledTask(binder, ProcessLocksWatchdog.class);
        bindSingletonScheduledTask(binder, ProcessQueueWatchdog.class);
        bindSingletonScheduledTask(binder, ProcessWaitWatchdog.class);

        binder.bind(Dispatcher.class).in(SINGLETON);
        newSetBinder(binder, BackgroundTask.class).addBinding().to(Dispatcher.class);

        newSetBinder(binder, BackgroundTask.class).addBinding().toProvider(EnqueuedTaskProvider.class).in(SINGLETON);
    }
}
