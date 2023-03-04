package com.walmartlabs.concord.server.boot;

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

import com.walmartlabs.concord.server.sdk.BackgroundTask;

import javax.inject.Inject;
import java.util.Set;

public class BackgroundTasks {

    private final Set<BackgroundTask> tasks;

    @Inject
    public BackgroundTasks(Set<BackgroundTask> tasks) {
        this.tasks = tasks;
    }

    public void start() {
        synchronized (tasks) {
            tasks.forEach(BackgroundTask::start);
        }
    }

    public void stop() {
        synchronized (tasks) {
            tasks.forEach(BackgroundTask::stop);
        }
    }

    public int count() {
        return tasks.size();
    }
}
