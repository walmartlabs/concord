package com.walmartlabs.concord.server.cfg;

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

import com.walmartlabs.ollie.config.Config;

import javax.inject.Inject;
import java.time.Duration;

public class EnqueueWorkersConfiguration {

    @Inject
    @Config("queue.enqueueWorkerCount")
    private int workersCount;

    @Inject
    @Config("queue.enqueueBatchSize")
    private int batchSize;

    @Inject
    @Config("queue.enqueueBatchEnabled")
    private boolean batchEnabled;

    @Inject
    @Config("queue.enqueuePollInterval")
    private Duration interval;

    public int getWorkersCount() {
        return workersCount;
    }

    public Duration getInterval() {
        return interval;
    }

    public boolean isBatchEnabled() {
        return batchEnabled;
    }

    public int getBatchSize() {
        return batchSize;
    }
}
