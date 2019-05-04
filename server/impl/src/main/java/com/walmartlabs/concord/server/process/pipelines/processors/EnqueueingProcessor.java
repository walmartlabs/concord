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
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.pipelines.processors.RepositoryProcessor.CommitInfo;
import com.walmartlabs.concord.server.process.pipelines.processors.RepositoryProcessor.RepositoryInfo;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.sdk.ProcessStatus;

import javax.inject.Inject;
import javax.inject.Named;
import javax.xml.bind.DatatypeConverter;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Moves the process into ENQUEUED status, filling in the necessary attributed.
 */
@Named
public class EnqueueingProcessor implements PayloadProcessor {

    private static final int MAX_COMMIT_ID_LENGTH = 128;

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

        // TODO replace with getStatus?
        ProcessEntry e = queueDao.get(processKey);
        if (e == null) {
            throw new ProcessException(processKey, "Process not found: " + processKey);
        }

        ProcessStatus s = e.status();
        if (s != ProcessStatus.PREPARING && s != ProcessStatus.RESUMING && s != ProcessStatus.SUSPENDED) {
            throw new ProcessException(processKey, "Invalid process status: " + s);
        }

        Map<String, Object> requirements = payload.getHeader(Payload.REQUIREMENTS);

        UUID repoId = null;
        String repoUrl = null;
        String repoPath = null;
        String commitId = null;
        String commitMsg = null;

        RepositoryInfo repoInfo = payload.getHeader(RepositoryProcessor.REPOSITORY_INFO_KEY);
        if (repoInfo != null) {
            repoId = repoInfo.getId();
            repoUrl = repoInfo.getUrl();
            repoPath = repoInfo.getPath();

            CommitInfo commitInfo = repoInfo.getCommitInfo();
            if (commitInfo != null) {
                commitId = commitInfo.getId();

                commitMsg = commitInfo.getMessage();
                if (commitMsg != null && commitMsg.length() > MAX_COMMIT_ID_LENGTH) {
                    commitMsg = commitMsg.substring(0, MAX_COMMIT_ID_LENGTH - 3) + "...";
                }
            }
        }

        Instant startAt = getStartAt(payload);
        Long processTimeout = getProcessTimeout(payload);
        boolean exclusive = isExclusive(payload);

        Set<String> handlers = payload.getHeader(Payload.PROCESS_HANDLERS);
        Map<String, Object> meta = getMeta(payload);
        queueDao.enqueue(processKey, tags, startAt, requirements, repoId, repoUrl, repoPath, commitId, commitMsg, processTimeout, handlers, meta, exclusive);

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
            return Boolean.valueOf((String) v);
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
