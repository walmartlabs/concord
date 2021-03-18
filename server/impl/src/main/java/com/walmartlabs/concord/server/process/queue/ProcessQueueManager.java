package com.walmartlabs.concord.server.process.queue;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.runtime.v2.model.ExclusiveMode;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.EventType;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.RequestUtils;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.process.event.NewProcessEvent;
import com.walmartlabs.concord.server.process.event.ProcessEventManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Named
public class ProcessQueueManager {

    private final ProcessQueueDao queueDao;
    private final ConcordObjectMapper objectMapper;
    private final ProcessKeyCache keyCache;
    private final ProcessEventManager eventManager;
    private final ProcessLogManager processLogManager;

    @Inject
    public ProcessQueueManager(ProcessQueueDao queueDao,
                               ConcordObjectMapper objectMapper,
                               ProcessKeyCache keyCache,
                               ProcessEventManager eventManager,
                               ProcessLogManager processLogManager) {

        this.queueDao = queueDao;
        this.eventManager = eventManager;
        this.objectMapper = objectMapper;
        this.keyCache = keyCache;
        this.processLogManager = processLogManager;
    }

    /**
     * Creates the initial queue record for the specified process payload.
     */
    public void insert(Payload payload, ProcessStatus status) {
        ProcessKey processKey = payload.getProcessKey();
        ProcessKind kind = payload.getHeader(Payload.PROCESS_KIND, ProcessKind.DEFAULT);
        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        UUID repoId = payload.getHeader(Payload.REPOSITORY_ID);
        UUID parentInstanceId = payload.getHeader(Payload.PARENT_INSTANCE_ID);
        UUID initiatorId = payload.getHeader(Payload.INITIATOR_ID);
        Map<String, Object> cfg = getCfg(payload);
        Map<String, Object> meta = getMeta(cfg);
        TriggeredByEntry triggeredBy = payload.getHeader(Payload.TRIGGERED_BY);
        String branchOrTag = MapUtils.getString(cfg, Constants.Request.REPO_BRANCH_OR_TAG);
        String commitId = MapUtils.getString(cfg, Constants.Request.REPO_COMMIT_ID);

        queueDao.tx(tx -> {
            queueDao.insert(tx, processKey, status, kind, parentInstanceId, projectId, repoId, branchOrTag, commitId, initiatorId, meta, triggeredBy);
            eventManager.insertStatusHistory(tx, processKey, status, Collections.emptyMap());
            processLogManager.createSystemSegment(tx, payload.getProcessKey());
        });
    }

    /**
     * Updates an existing record, moving the process into the ENQUEUED status.
     */
    public boolean enqueue(Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        ProcessStatus s = queueDao.getStatus(processKey);
        if (s == null) {
            throw new ProcessException(processKey, "Process not found: " + processKey);
        }

        if (s == ProcessStatus.CANCELLED) {
            // the process was cancelled while going through EnqueueProcessPipeline
            // (e.g. it was a process in an "exclusive" group)
            return false;
        }

        if (s != ProcessStatus.PREPARING && s != ProcessStatus.RESUMING && s != ProcessStatus.SUSPENDED) {
            // something's wrong (e.g. someone tried to change the process' status directly in the DB and was unlucky)
            throw new ProcessException(processKey, "Invalid process status: " + s);
        }

        Set<String> tags = payload.getHeader(Payload.PROCESS_TAGS);
        OffsetDateTime startAt = PayloadUtils.getStartAt(payload);
        Map<String, Object> requirements = PayloadUtils.getRequirements(payload);
        Long processTimeout = getProcessTimeout(payload);
        Long suspendTimeout = getSuspendTimeout(payload);
        Set<String> handlers = payload.getHeader(Payload.PROCESS_HANDLERS);
        Map<String, Object> meta = getMeta(getCfg(payload));
        Imports imports = payload.getHeader(Payload.IMPORTS);
        ExclusiveMode exclusive = PayloadUtils.getExclusive(payload);
        String runtime = payload.getHeader(Payload.RUNTIME);
        List<String> dependencies = payload.getHeader(Payload.DEPENDENCIES);

        queueDao.tx(tx -> {
            queueDao.enqueue(tx, processKey, tags, startAt, requirements, processTimeout, handlers, meta, imports, exclusive, runtime, dependencies, suspendTimeout);
            eventManager.insertStatusHistory(tx, processKey, ProcessStatus.ENQUEUED, Collections.emptyMap());
        });

        return true;
    }

    /**
     * @see #updateStatus(DSLContext, ProcessKey, ProcessStatus, Map)
     */
    public void updateStatus(ProcessKey processKey, ProcessStatus status) {
        updateStatus(processKey, status, Collections.emptyMap());
    }

    /**
     * @see #updateStatus(DSLContext, ProcessKey, ProcessStatus, Map)
     */
    public void updateStatus(DSLContext tx, ProcessKey processKey, ProcessStatus status) {
        updateStatus(tx, processKey, status, Collections.emptyMap());
    }

    /**
     * @see #updateStatus(DSLContext, ProcessKey, ProcessStatus, Map)
     */
    public void updateStatus(ProcessKey processKey, ProcessStatus status, Map<String, Object> statusPayload) {
        queueDao.tx(tx -> updateStatus(tx, processKey, status, statusPayload));
    }

    /**
     * Updates the process' status. Adds a process status history event with an optional {@code statusPayload}.
     */
    public void updateStatus(DSLContext tx, ProcessKey processKey, ProcessStatus status, Map<String, Object> statusPayload) {
        queueDao.updateStatus(tx, processKey, status);
        eventManager.insertStatusHistory(tx, processKey, status, statusPayload);
    }

    /**
     * Updates the process' status but only if it's in the {@code expected} status.
     *
     * @return {@code true} if the process was updated
     */
    public boolean updateExpectedStatus(ProcessKey processKey, ProcessStatus expected, ProcessStatus status) {
        return queueDao.txResult(tx -> {
            boolean success = queueDao.updateStatus(tx, processKey, expected, status);
            eventManager.insertStatusHistory(tx, processKey, status, Collections.emptyMap());
            return success;
        });
    }

    /**
     * Updates status of multiple processes but only if their current status is
     * in the {@code expected} list of statuses.
     *
     * @return {@code true} if every processes was updated
     */
    public boolean updateExpectedStatus(List<ProcessKey> processKeys, List<ProcessStatus> expected, ProcessStatus status) {
        return queueDao.txResult(tx -> {
            boolean success = queueDao.updateStatus(processKeys, expected, status);
            eventManager.insertStatusHistory(tx, processKeys, status);
            return success;
        });
    }

    /**
     * @see #updateAgentId(DSLContext, ProcessKey, String, ProcessStatus)
     */
    public void updateAgentId(ProcessKey processKey, String agentId, ProcessStatus status) {
        queueDao.tx(tx -> updateAgentId(tx, processKey, agentId, status));
    }

    /**
     * Updates the process' agent ID and status.
     */
    public void updateAgentId(DSLContext tx, ProcessKey processKey, String agentId, ProcessStatus status) {
        queueDao.updateAgentId(tx, processKey, agentId, status);
        eventManager.insertStatusHistory(tx, processKey, status, Collections.emptyMap());
    }

    /**
     * @see #addWait(DSLContext, ProcessKey, AbstractWaitCondition)
     */
    public void addWait(ProcessKey key, AbstractWaitCondition wait) {
        queueDao.tx(tx -> addWait(tx, key, wait));
    }

    /**
     * Add the process' wait conditions. Adds a wait condition history event.
     */
    public void addWait(DSLContext tx, ProcessKey key, AbstractWaitCondition wait) {
        if (wait == null) {
            return;
        }

        queueDao.addWait(tx, key, wait);

//        addWaitEvent(tx, processKey, wait);
    }

    private void addWaitEvent(DSLContext tx, ProcessKey processKey, AbstractWaitCondition wait) {
        Map<String, Object> eventData = objectMapper.convertToMap(wait != null ? wait : new NoneCondition());
        NewProcessEvent e = NewProcessEvent.builder()
                .processKey(processKey)
                .eventType(EventType.PROCESS_WAIT.name())
                .data(eventData)
                .build();
        eventManager.event(tx, Collections.singletonList(e));
    }

    public void setWait(ProcessKey key, List<AbstractWaitCondition> waits) {
        queueDao.tx(tx -> setWait(tx, key, waits));
    }

    public void setWait(DSLContext tx, ProcessKey processKey, List<AbstractWaitCondition> waits) {
        if (waits.isEmpty()) {
           waits = null;
        }

        queueDao.setWait(tx, processKey, waits);
    }

    public void updateExclusive(DSLContext tx, ProcessKey processKey, ExclusiveMode exclusive) {
        queueDao.updateExclusive(tx, processKey, exclusive);
    }

    public ProcessEntry get(PartialProcessKey partialProcessKey) {
        ProcessKey key = keyCache.get(partialProcessKey.getInstanceId());
        if (key == null) {
            return null;
        }
        return queueDao.get(key);
    }

    public ProcessInitiatorEntry getInitiator(PartialProcessKey partialProcessKey) {
        ProcessKey key = keyCache.get(partialProcessKey.getInstanceId());
        if (key == null) {
            return null;
        }

        return queueDao.getInitiator(key);
    }

    public ProcessEntry get(PartialProcessKey partialProcessKey, Set<ProcessDataInclude> includes) {
        ProcessKey key = keyCache.get(partialProcessKey.getInstanceId());
        if (key == null) {
            return null;
        }
        return queueDao.get(key, includes);
    }

    private static Map<String, Object> getCfg(Payload payload) {
        return payload.getHeader(Payload.CONFIGURATION, Collections.emptyMap());
    }

    private static Map<String, Object> getMeta(Map<String, Object> cfg) {
        Map<String, Object> m = MapUtils.getMap(cfg, Constants.Request.META, Collections.emptyMap());
        m = new HashMap<>(m);
        m.put(Constants.Meta.SYSTEM_GROUP, Collections.singletonMap(Constants.Meta.REQUEST_ID, RequestUtils.getRequestId()));
        m.put(Constants.Request.ENTRY_POINT_KEY, cfg.get(Constants.Request.ENTRY_POINT_KEY));
        return m;
    }

    private static Long getProcessTimeout(Payload p) {
        return getTimeout(p, Constants.Request.PROCESS_TIMEOUT);
    }

    private static Long getSuspendTimeout(Payload p) {
        return getTimeout(p, Constants.Request.SUSPEND_TIMEOUT);
    }

    private static Long getTimeout(Payload p, String paramName) {
        Map<String, Object> cfg = p.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            return null;
        }

        Object processTimeout = cfg.get(paramName);
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

        throw new IllegalArgumentException("Invalid '" + paramName + "' value: expected an ISO-8601 value, got: " + processTimeout);
    }
}
