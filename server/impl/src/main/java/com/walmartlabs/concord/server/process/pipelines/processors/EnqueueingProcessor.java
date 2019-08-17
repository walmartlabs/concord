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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.queueclient.message.Imports;
import com.walmartlabs.concord.server.sdk.ProcessStatus;

import javax.inject.Inject;
import javax.inject.Named;
import javax.xml.bind.DatatypeConverter;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Moves the process into ENQUEUED status, filling in the necessary attributed.
 */
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
        ProcessKey processKey = payload.getProcessKey();

        Set<String> tags = payload.getHeader(Payload.PROCESS_TAGS);

        ProcessStatus s = queueDao.getStatus(processKey.getInstanceId());
        if (s == null) {
            throw new ProcessException(processKey, "Process not found: " + processKey);
        }

        if (s != ProcessStatus.PREPARING && s != ProcessStatus.RESUMING && s != ProcessStatus.SUSPENDED) {
            throw new ProcessException(processKey, "Invalid process status: " + s);
        }

        Map<String, Object> requirements = payload.getHeader(Payload.REQUIREMENTS);

        Instant startAt = getStartAt(payload);
        Long processTimeout = getProcessTimeout(payload);
        boolean exclusive = isExclusive(payload);

        Set<String> handlers = payload.getHeader(Payload.PROCESS_HANDLERS);
        Map<String, Object> meta = getMeta(payload);
        Imports imports = payload.getHeader(Payload.IMPORTS);
        queueDao.enqueue(processKey, tags, startAt, requirements, processTimeout, handlers, meta, exclusive, imports);

        return chain.process(payload);
    }

    @SuppressWarnings("unchecked")
    private static Instant getStartAt(Payload p) {
        Map<String, Object> cfg = p.getHeader(Payload.REQUEST_DATA_MAP);
        if (cfg == null) {
            return null;
        }

        String k = Constants.Request.START_AT_KEY;
        Object v = cfg.get(k);
        if (v == null) {
            return null;
        }

        if (v instanceof String) {
            Calendar c;
            try {
                c = DatatypeConverter.parseDateTime((String) v);
            } catch (DateTimeParseException e) {
                throw new ProcessException(p.getProcessKey(), "Invalid '" + k + "' format, expected an ISO-8601 value, got: " + v);
            }

            if (c.before(Calendar.getInstance())) {
                throw new ProcessException(p.getProcessKey(), "Invalid '" + k + "' value, can't be in the past: " + v);
            }

            return c.toInstant();
        }

        throw new ProcessException(p.getProcessKey(), "Invalid '" + k + "' value, expected an ISO-8601 value, got: " + v);
    }

    @SuppressWarnings("unchecked")
    private static Long getProcessTimeout(Payload p) {
        Map<String, Object> cfg = p.getHeader(Payload.REQUEST_DATA_MAP);
        if (cfg == null) {
            return null;
        }

        Object processTimeout = cfg.get(Constants.Request.PROCESS_TIMEOUT);
        if (processTimeout == null) {
            return null;
        }

        if (processTimeout instanceof String) {
            Duration duration = Duration.parse((CharSequence) processTimeout);
            return duration.get(ChronoUnit.SECONDS);
        }

        if (processTimeout instanceof Number) {
            return ((Number) processTimeout).longValue();
        }

        throw new IllegalArgumentException("Invalid '" + Constants.Request.PROCESS_TIMEOUT + "' value: expected an ISO-8601 value, got: " + processTimeout);
    }

    @SuppressWarnings("unchecked")
    private static boolean isExclusive(Payload p) {
        Map<String, Object> cfg = p.getHeader(Payload.REQUEST_DATA_MAP);
        if (cfg == null) {
            return false;
        }

        Object v = cfg.get(Constants.Request.EXCLUSIVE_EXEC);
        if (v == null) {
            return false;
        }

        if (v instanceof String) {
            return Boolean.parseBoolean((String) v);
        }

        if (v instanceof Boolean) {
            return (boolean) v;
        }

        throw new IllegalArgumentException("Invalid '" + Constants.Request.EXCLUSIVE_EXEC + "' value: expected a boolean value, got: " + v);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMeta(Payload p) {
        Map<String, Object> cfg = p.getHeader(Payload.REQUEST_DATA_MAP, Collections.emptyMap());
        return (Map<String, Object>) cfg.get(Constants.Request.META);
    }
}
