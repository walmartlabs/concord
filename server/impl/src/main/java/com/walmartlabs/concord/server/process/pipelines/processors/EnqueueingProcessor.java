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
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadUtils;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Instant;
import java.util.Map;

/**
 * Moves the process into ENQUEUED status, filling in the necessary attributes.
 */
@Named
public class EnqueueingProcessor implements PayloadProcessor {

    private final ProcessQueueManager queueManager;
    private final LogManager logManager;

    @Inject
    public EnqueueingProcessor(ProcessQueueManager queueManager, LogManager logManager) {
        this.queueManager = queueManager;
        this.logManager = logManager;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        queueManager.enqueue(payload);

        ProcessKey processKey = payload.getProcessKey();

        Map<String, Object> requirements = PayloadUtils.getRequirements(payload);
        Instant startAt = PayloadUtils.getStartAt(payload);

        if (startAt == null) {
            logManager.info(processKey, "Enqueued. Waiting for an agent (requirements={})...", requirements);
        } else {
            logManager.info(processKey, "Enqueued. Starting at {} (requirements={})...", startAt, requirements);
        }

        return chain.process(payload);
    }
}
