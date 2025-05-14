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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BackgroundTasks {

    private static final Logger log = LoggerFactory.getLogger(BackgroundTasks.class);

    private final Set<BackgroundTask> tasks;
    private final Lock controlMutex = new ReentrantLock();

    @Inject
    public BackgroundTasks(Set<BackgroundTask> tasks) {
        this.tasks = Set.copyOf(tasks);
    }

    public void start() {
        controlMutex.lock();
        log.info("start -> starting {} task(s)", tasks.size());
        try {
            tasks.forEach(BackgroundTask::start);
        } finally {
            controlMutex.unlock();
        }
    }

    public void stop() {
        controlMutex.lock();
        try {
            tasks.forEach(BackgroundTask::stop);
        } finally {
            controlMutex.unlock();
        }
    }

    public int count() {
        return tasks.size();
    }
}
