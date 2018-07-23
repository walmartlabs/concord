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

import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.ProcessKind;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class InitialQueueEntryProcessor implements PayloadProcessor {

    private final ProcessQueueDao queueDao;

    @Inject
    public InitialQueueEntryProcessor(ProcessQueueDao queueDao) {
        this.queueDao = queueDao;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();
        ProcessKind kind = payload.getHeader(Payload.PROCESS_KIND, ProcessKind.DEFAULT);
        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        UUID parentInstanceId = payload.getHeader(Payload.PARENT_INSTANCE_ID);
        String initiator = payload.getHeader(Payload.INITIATOR);

        queueDao.insertInitial(instanceId, kind, parentInstanceId, projectId, initiator);

        return chain.process(payload);
    }
}
