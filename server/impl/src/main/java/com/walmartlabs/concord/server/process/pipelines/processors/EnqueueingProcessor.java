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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;

import javax.inject.Inject;
import javax.inject.Named;
import javax.xml.bind.DatatypeConverter;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Map;
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

        Instant startAt = getStartAt(payload);

        queueDao.update(instanceId, ProcessStatus.ENQUEUED, tags, startAt);
        return chain.process(payload);
    }

    @SuppressWarnings("unchecked")
    private static Instant getStartAt(Payload p) {
        Map<String, Object> cfg = p.getHeader(Payload.REQUEST_DATA_MAP);
        if (cfg == null) {
            return null;
        }

        Object v = cfg.get(Constants.Request.START_AT_KEY);
        if (v == null) {
            return null;
        }

        if (v instanceof String) {
            Calendar c;
            try {
                c = DatatypeConverter.parseDateTime((String) v);
            } catch (DateTimeParseException e) {
                throw new ProcessException(p.getInstanceId(), "Invalid 'startAt' format, expected an ISO-8601 value, got: " + v);
            }

            if (c.before(Calendar.getInstance())) {
                throw new ProcessException(p.getInstanceId(), "Invalid 'startAt' value, can't be in the past: " + v);
            }

            return c.toInstant();
        }

        throw new ProcessException(p.getInstanceId(), "Invalid 'startAt' value, expected an ISO-8601 value, got: " + v);
    }
}
