package com.walmartlabs.concord.server;

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

import com.google.inject.Inject;
import com.walmartlabs.concord.server.sdk.BackgroundTask;
import com.walmartlabs.ollie.lifecycle.Lifecycle;
import org.eclipse.sisu.EagerSingleton;

import javax.inject.Named;
import java.util.Set;

@Named
@EagerSingleton
public class BackgroundTaskLifecycle implements Lifecycle {

    private final Set<BackgroundTask> tasks;

    @Inject
    public BackgroundTaskLifecycle(Set<BackgroundTask> tasks) {
        this.tasks = tasks;
    }

    @Override
    public void start() {
        tasks.forEach(BackgroundTask::start);
    }

    @Override
    public void stop() {
        tasks.forEach(BackgroundTask::stop);
    }
}
