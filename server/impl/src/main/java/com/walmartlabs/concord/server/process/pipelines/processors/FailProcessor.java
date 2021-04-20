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
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class FailProcessor implements ExceptionProcessor {

    private final ProcessQueueDao queueDao;
    private final ProcessQueueManager queueManager;
    private final ProcessLogManager logManager;

    @Inject
    public FailProcessor(ProcessQueueDao queueDao, ProcessQueueManager queueManager, ProcessLogManager logManager) {
        this.queueDao = queueDao;
        this.logManager = logManager;
        this.queueManager = queueManager;
    }

    @Override
    public void process(Payload payload, Exception e) {
        ProcessKey processKey = payload.getProcessKey();

        boolean hasQueueRecord = queueDao.exists(processKey);
        if (!hasQueueRecord) {
            // the process failed before we had a chance to create the initial queue record
            return;
        }

        logManager.error(processKey, "Process failed: {}", e.getMessage());

        if (!(e instanceof InvalidProcessStateException)) {
            queueManager.updateStatus(processKey, ProcessStatus.FAILED);
        }
    }
}
