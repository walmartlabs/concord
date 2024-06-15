package com.walmartlabs.concord.server.process.queue;

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

import com.google.inject.Injector;
import com.google.inject.Provider;
import com.walmartlabs.concord.server.cfg.EnqueueWorkersConfiguration;
import com.walmartlabs.concord.server.sdk.BackgroundTask;

import javax.inject.Inject;

public class EnqueuedTaskProvider implements Provider<BackgroundTask> {

    private final BackgroundTask task;

    @Inject
    public EnqueuedTaskProvider(Injector injector, EnqueueWorkersConfiguration cfg) {
        if (cfg.isBatchEnabled()) {
            this.task = injector.getInstance(EnqueuedBatchTask.class);
        } else {
            this.task = injector.getInstance(EnqueuedTask.class);
        }
    }

    @Override
    public BackgroundTask get() {
        return task;
    }
}
