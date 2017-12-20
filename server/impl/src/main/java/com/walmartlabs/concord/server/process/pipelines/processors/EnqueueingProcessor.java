package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Set;
import java.util.UUID;

@Named
public class EnqueueingProcessor implements PayloadProcessor {

    private final ProcessQueueDao queueDao;

    @Inject
    public EnqueueingProcessor(ProcessQueueDao queueDao) {
        this.queueDao = queueDao;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();
        Set<String> tags = payload.getHeader(Payload.PROCESS_TAGS);

        ProcessEntry e = queueDao.get(instanceId);
        if (e == null) {
            throw new ProcessException(instanceId, "Process not found: " + instanceId);
        }

        ProcessStatus s = e.getStatus();
        if (s != ProcessStatus.PREPARING && s != ProcessStatus.RESUMING && s != ProcessStatus.SUSPENDED) {
            throw new ProcessException(instanceId, "Invalid process status: " + s);
        }

        queueDao.update(instanceId, ProcessStatus.ENQUEUED, tags);
        return chain.process(payload);
    }
}
