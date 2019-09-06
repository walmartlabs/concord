package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import com.walmartlabs.concord.db.DatabaseModule;
import com.walmartlabs.concord.server.metrics.MetricModule;
import com.walmartlabs.concord.server.process.queue.EnqueuedTask;
import com.walmartlabs.concord.server.task.TaskScheduler;

public class ServerModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new MetricModule());
        install(new DatabaseModule());

        Multibinder<BackgroundTask> tasks = Multibinder.newSetBinder(binder(), BackgroundTask.class);
        tasks.addBinding().to(com.walmartlabs.concord.server.agent.dispatcher.Dispatcher.class);
        tasks.addBinding().to(com.walmartlabs.concord.server.process.queue.dispatcher.Dispatcher.class);
        tasks.addBinding().to(EnqueuedTask.class);
        tasks.addBinding().to(TaskScheduler.class);
    }
}
