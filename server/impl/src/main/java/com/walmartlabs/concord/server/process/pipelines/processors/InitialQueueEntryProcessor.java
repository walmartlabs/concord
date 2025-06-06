package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.ProcessStatus;

import javax.inject.Inject;

public class InitialQueueEntryProcessor implements PayloadProcessor {

    private final ProcessQueueManager queueManager;

    @Inject
    public InitialQueueEntryProcessor(ProcessQueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        queueManager.insert(payload, ProcessStatus.PREPARING);
        return chain.process(payload);
    }
}
