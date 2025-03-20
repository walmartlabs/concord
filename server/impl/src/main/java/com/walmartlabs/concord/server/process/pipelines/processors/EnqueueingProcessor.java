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
import com.walmartlabs.concord.server.process.PayloadUtils;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Moves the process into ENQUEUED status, filling in the necessary attributes.
 */
public class EnqueueingProcessor implements PayloadProcessor {

    private final ProcessQueueManager queueManager;
    private final ProcessLogManager logManager;

    @Inject
    public EnqueueingProcessor(ProcessQueueManager queueManager, ProcessLogManager logManager) {
        this.queueManager = queueManager;
        this.logManager = logManager;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        boolean enqueued = queueManager.enqueue(payload);
        if (!enqueued) {
            // the process was rejected but it was not an error
            // (e.g. "exclusive" processes can be rejected before reaching the ENQUEUED status)
            return payload;
        }

        ProcessKey processKey = payload.getProcessKey();

        Map<String, Object> requirements = PayloadUtils.getRequirements(payload);
        OffsetDateTime startAt = PayloadUtils.getStartAt(payload);

        if (startAt == null) {
            logManager.info(processKey, "Enqueued. Waiting for an agent (requirements={})...", requirements);
        } else {
            logManager.info(processKey, "Enqueued. Starting at {} (requirements={})...", startAt, requirements);
        }

        return chain.process(payload);
    }
}
