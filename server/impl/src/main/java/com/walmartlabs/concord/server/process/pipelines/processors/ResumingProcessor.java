package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Moves the process into RESUMING status if current process eq SUSPENDED.
 */
@Named
public class ResumingProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(ResumingProcessor.class);

    private final ProcessQueueManager queueManager;

    @Inject
    public ResumingProcessor(ProcessQueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        boolean updated = queueManager.updateExpectedStatus(processKey, ProcessStatus.SUSPENDED, ProcessStatus.RESUMING);
        if (updated) {
            return chain.process(payload);
        }

        log.warn("process ['{}'] -> process is not suspended, can't resume", processKey);
        throw new InvalidProcessStateException("Process is not suspended, can't resume");
    }
}
