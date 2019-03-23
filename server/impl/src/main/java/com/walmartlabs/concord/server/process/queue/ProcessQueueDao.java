package com.walmartlabs.concord.server.process.queue;

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.sdk.EventType;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.jooq.Tables;
import com.walmartlabs.concord.server.jooq.tables.ProcessCheckpoints;
import com.walmartlabs.concord.server.jooq.tables.ProcessEvents;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessQueueRecord;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.process.ProcessEntry.ProcessCheckpointEntry;
import com.walmartlabs.concord.server.process.ProcessEntry.ProcessStatusHistoryEntry;
import com.walmartlabs.concord.server.process.ProcessEntry.ProcessWaitHistoryEntry;
import com.walmartlabs.concord.server.process.event.EventDao;
import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.util.postgres.PostgresDSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.db.PgUtils.jsonText;
import static com.walmartlabs.concord.db.PgUtils.toChar;
import static com.walmartlabs.concord.server.jooq.Tables.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.Tables.USERS;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.ProcessCheckpoints.PROCESS_CHECKPOINTS;
import static com.walmartlabs.concord.server.jooq.tables.ProcessEvents.PROCESS_EVENTS;
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static org.jooq.impl.DSL.*;

@Named
public class ProcessQueueDao extends AbstractDao {

    private static final Set<ProcessDataInclude> DEFAULT_INCLUDES = Collections.singleton(ProcessDataInclude.CHILDREN_IDS);

    private static final TypeReference<List<ProcessCheckpointEntry>> LIST_OF_CHECKPOINTS = new TypeReference<List<ProcessCheckpointEntry>>() {
    };
    private static final TypeReference<ProcessStatusHistoryEntry> STATUS_HISTORY_ENTRY = new TypeReference<ProcessStatusHistoryEntry>() {
    };
    private static final TypeReference<ProcessWaitHistoryEntry> WAIT_HISTORY_ENTRY = new TypeReference<ProcessWaitHistoryEntry>() {
    };
    private static final TypeReference<List<ProcessStatusHistoryEntry>> LIST_OF_STATUS_HISTORY = new TypeReference<List<ProcessStatusHistoryEntry>>() {
    };

    private final List<ProcessQueueEntryFilter> filters;

    private final EventDao eventDao;
    private final ConcordObjectMapper objectMapper;
    private final ProcessQueueLock queueLock;

    @Inject
    protected ProcessQueueDao(@Named("app") Configuration cfg,
                              List<ProcessQueueEntryFilter> filters,
                              EventDao eventDao,
                              ProcessQueueLock queueLock,
                              ConcordObjectMapper objectMapper) {
        super(cfg);
        this.filters = filters;
        this.eventDao = eventDao;
        this.queueLock = queueLock;
        this.objectMapper = objectMapper;
    }

    public ProcessKey getKey(UUID instanceId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROCESS_QUEUE.CREATED_AT)
                    .from(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                    .fetchOne(r -> new ProcessKey(instanceId, r.value1()));
        }
    }

    public void insertInitial(ProcessKey processKey, ProcessKind kind, UUID parentInstanceId,
                              UUID projectId, UUID initiatorId, Map<String, Object> meta) {

        tx(tx -> insertInitial(tx, processKey, kind, parentInstanceId, projectId, initiatorId, meta));
    }

    private void insertInitial(DSLContext tx, ProcessKey processKey, ProcessKind kind, UUID parentInstanceId,
                               UUID projectId, UUID initiatorId, Map<String, Object> meta) {

        tx.insertInto(PROCESS_QUEUE)
                .columns(PROCESS_QUEUE.INSTANCE_ID,
                        PROCESS_QUEUE.PROCESS_KIND,
                        PROCESS_QUEUE.PARENT_INSTANCE_ID,
                        PROCESS_QUEUE.PROJECT_ID,
                        PROCESS_QUEUE.CREATED_AT,
                        PROCESS_QUEUE.INITIATOR_ID,
                        PROCESS_QUEUE.CURRENT_STATUS,
                        PROCESS_QUEUE.LAST_UPDATED_AT,
                        PROCESS_QUEUE.META)
                .values(value(processKey.getInstanceId()),
                        value(kind.toString()),
                        value(parentInstanceId),
                        value(projectId),
                        value(processKey.getCreatedAt()),
                        value(initiatorId),
                        value(ProcessStatus.PREPARING.toString()),
                        currentTimestamp(),
                        field("?::jsonb", objectMapper.serialize(meta)))
                .execute();

        insertStatusHistory(tx, processKey, ProcessStatus.PREPARING);
    }

    public void updateAgentId(ProcessKey processKey, String agentId, ProcessStatus status) {
        tx(tx -> updateAgentId(tx, processKey, agentId, status));
    }

    public void updateAgentId(DSLContext tx, ProcessKey processKey, String agentId, ProcessStatus status) {
        UUID instanceId = processKey.getInstanceId();

        int i = tx.update(PROCESS_QUEUE)
                .set(PROCESS_QUEUE.CURRENT_STATUS, status.toString())
                .set(PROCESS_QUEUE.LAST_AGENT_ID, agentId)
                .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp())
                .set(PROCESS_QUEUE.LAST_RUN_AT, createRunningAtValue(status))
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                .execute();

        if (i != 1) {
            throw new DataAccessException("Invalid number of rows updated: " + i);
        }

        insertStatusHistory(tx, processKey, status);
    }

    private static Field<Timestamp> createRunningAtValue(ProcessStatus status) {
        return when(PROCESS_QUEUE.CURRENT_STATUS.eq(ProcessStatus.RUNNING.toString()), PROCESS_QUEUE.LAST_RUN_AT)
                .otherwise(when(value(status.toString()).eq(ProcessStatus.RUNNING.toString()), currentTimestamp())
                        .otherwise(PROCESS_QUEUE.LAST_RUN_AT));
    }

    public void enqueue(ProcessKey processKey, Set<String> tags, Instant startAt,
                        Map<String, Object> requirements, UUID repoId, String repoUrl, String repoPath,
                        String commitId, String commitMsg, Long processTimeout,
                        Set<String> handlers, Map<String, Object> meta,
                        boolean exclusive) {

        tx(tx -> {
            UpdateSetMoreStep<ProcessQueueRecord> q = tx.update(PROCESS_QUEUE)
                    .set(PROCESS_QUEUE.CURRENT_STATUS, ProcessStatus.ENQUEUED.toString())
                    .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp());

            if (tags != null) {
                q.set(PROCESS_QUEUE.PROCESS_TAGS, toArray(tags));
            }

            if (startAt != null) {
                q.set(PROCESS_QUEUE.START_AT, Timestamp.from(startAt));
            }

            if (requirements != null) {
                q.set(PROCESS_QUEUE.REQUIREMENTS, field("?::jsonb", String.class, objectMapper.serialize(requirements)));
            }

            if (repoId != null) {
                q.set(PROCESS_QUEUE.REPO_ID, repoId);
            }

            if (repoUrl != null) {
                q.set(PROCESS_QUEUE.REPO_URL, repoUrl);
            }

            if (repoPath != null) {
                q.set(PROCESS_QUEUE.REPO_PATH, repoPath);
            }

            if (commitId != null) {
                q.set(PROCESS_QUEUE.COMMIT_ID, commitId);
            }

            if (commitMsg != null) {
                q.set(PROCESS_QUEUE.COMMIT_MSG, commitMsg);
            }

            if (processTimeout != null) {
                q.set(PROCESS_QUEUE.TIMEOUT, processTimeout);
            }

            if (handlers != null) {
                q.set(PROCESS_QUEUE.HANDLERS, toArray(handlers));
            }

            if (meta != null) {
                q.set(PROCESS_QUEUE.META, field(coalesce(PROCESS_QUEUE.META, field("?::jsonb", String.class, "{}")) + " || ?::jsonb", String.class, objectMapper.serialize(meta)));
            }


            q.set(PROCESS_QUEUE.IS_EXCLUSIVE, exclusive);

            int i = q
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(processKey.getInstanceId()))
                    .execute();

            if (i != 1) {
                throw new DataAccessException("Invalid number of rows updated: " + i);
            }

            insertStatusHistory(tx, processKey, ProcessStatus.ENQUEUED);
        });
    }

    public void updateStatus(ProcessKey processKey, ProcessStatus status) {
        updateStatus(processKey, status, Collections.emptyMap());
    }

    public void updateStatus(ProcessKey processKey, ProcessStatus status, Map<String, Object> statusPayload) {
        tx(tx -> updateStatus(tx, processKey, status, statusPayload));
    }

    private void updateStatus(DSLContext tx, ProcessKey processKey, ProcessStatus status) {
        updateStatus(tx, processKey, status, Collections.emptyMap());
    }

    private void updateStatus(DSLContext tx, ProcessKey processKey, ProcessStatus status, Map<String, Object> statusPayload) {
        UUID instanceId = processKey.getInstanceId();

        tx.update(PROCESS_QUEUE)
                .set(PROCESS_QUEUE.CURRENT_STATUS, status.toString())
                .set(PROCESS_QUEUE.LAST_RUN_AT, createRunningAtValue(status))
                .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp())
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                .execute();

        insertStatusHistory(tx, processKey, status, statusPayload);
    }

    public boolean updateStatus(ProcessKey processKey, ProcessStatus expected, ProcessStatus status) {
        UUID instanceId = processKey.getInstanceId();

        return txResult(tx -> {
            int i = tx.update(PROCESS_QUEUE)
                    .set(PROCESS_QUEUE.CURRENT_STATUS, status.toString())
                    .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp())
                    .set(PROCESS_QUEUE.LAST_RUN_AT, createRunningAtValue(status))
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_QUEUE.CURRENT_STATUS.eq(expected.toString())))
                    .execute();

            insertStatusHistory(tx, processKey, status);

            return i == 1;
        });
    }

    public boolean updateMeta(PartialProcessKey processKey, Map<String, Object> meta) {
        UUID instanceId = processKey.getInstanceId();

        return txResult(tx -> {
            int i = tx.update(PROCESS_QUEUE)
                    .set(PROCESS_QUEUE.META, field(coalesce(PROCESS_QUEUE.META, field("?::jsonb", String.class, "{}")) + " || ?::jsonb", String.class, objectMapper.serialize(meta)))
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                    .execute();

            return i == 1;
        });
    }

    public boolean removeMeta(PartialProcessKey processKey, String key) {
        UUID instanceId = processKey.getInstanceId();

        return txResult(tx -> {
            Field<String> v = field("{0}", String.class, PROCESS_QUEUE.META).minus(value(key));
            int i = tx.update(PROCESS_QUEUE)
                    .set(PROCESS_QUEUE.META, v)
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                    .execute();

            return i == 1;
        });
    }

    public boolean updateStatus(List<ProcessKey> processKeys, ProcessStatus status, List<ProcessStatus> expected) {
        return txResult(tx -> {
            List<UUID> instanceIds = processKeys.stream()
                    .map(PartialProcessKey::getInstanceId)
                    .collect(Collectors.toList());

            UpdateConditionStep q = tx.update(PROCESS_QUEUE)
                    .set(PROCESS_QUEUE.CURRENT_STATUS, status.toString())
                    .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp())
                    .set(PROCESS_QUEUE.LAST_RUN_AT, createRunningAtValue(status))
                    .where(PROCESS_QUEUE.INSTANCE_ID.in(instanceIds));

            if (expected != null) {
                List<String> l = expected.stream()
                        .map(Enum::toString)
                        .collect(Collectors.toList());

                q.and(PROCESS_QUEUE.CURRENT_STATUS.in(l));
            }

            int i = q.execute();

            insertStatusHistory(tx, processKeys, status);

            return i == processKeys.size();
        });
    }

    public void removeHandler(PartialProcessKey processKey, String handler) {
        tx(tx -> tx.update(PROCESS_QUEUE)
                .set(PROCESS_QUEUE.HANDLERS, PostgresDSL.arrayRemove(PROCESS_QUEUE.HANDLERS, field("{0}::text", String.class, handler)))
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(processKey.getInstanceId()))
                .execute());
    }

    public boolean touch(UUID instanceId) {
        return txResult(tx -> {
            int i = tx.update(PROCESS_QUEUE)
                    .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp())
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                    .execute();

            return i == 1;
        });
    }

    public ProcessEntry get(PartialProcessKey processKey) {
        return get(processKey, DEFAULT_INCLUDES);
    }

    public ProcessEntry get(PartialProcessKey processKey, Set<ProcessDataInclude> includes) {
        return txResult(tx -> get(tx, processKey, includes));
    }

    public ProcessStatus getStatus(UUID instanceId) {
        try (DSLContext tx = DSL.using(cfg)) {
            String status = tx.select(PROCESS_QUEUE.CURRENT_STATUS)
                    .from(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                    .fetchOne(PROCESS_QUEUE.CURRENT_STATUS);
            if (status == null) {
                return null;
            }
            return ProcessStatus.valueOf(status);
        }
    }

    public List<ProcessEntry> get(List<PartialProcessKey> processKeys) {
        try (DSLContext tx = DSL.using(cfg)) {
            List<UUID> instanceIds = processKeys.stream()
                    .map(PartialProcessKey::getInstanceId)
                    .collect(Collectors.toList());

            SelectQuery<Record> query = buildSelect(tx, ProcessFilter.builder().build());

            query.addConditions(PROCESS_QUEUE.INSTANCE_ID.in(instanceIds));
            return query.fetch(this::toEntry);
        }
    }

    public List<IdAndStatus> getCascade(PartialProcessKey parentKey) {
        UUID parentInstanceId = parentKey.getInstanceId();

        try (DSLContext tx = DSL.using(cfg)) {
            return tx.withRecursive("children").as(
                    select(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.CREATED_AT, PROCESS_QUEUE.CURRENT_STATUS).from(PROCESS_QUEUE)
                            .where(PROCESS_QUEUE.INSTANCE_ID.eq(parentInstanceId))
                            .unionAll(
                                    select(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.CREATED_AT, PROCESS_QUEUE.CURRENT_STATUS).from(PROCESS_QUEUE)
                                            .join(name("children"))
                                            .on(PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(
                                                    field(name("children", "INSTANCE_ID"), UUID.class)))))
                    .select()
                    .from(name("children"))
                    .fetch(r -> new IdAndStatus(new ProcessKey(r.get(0, UUID.class), r.get(1, Timestamp.class)), ProcessStatus.valueOf(r.get(2, String.class))));
        }
    }

    public UUID getOrgId(UUID instanceId) {
        try (DSLContext tx = DSL.using(cfg)) {
            Field<UUID> orgId = select(PROJECTS.ORG_ID)
                    .from(PROJECTS)
                    .where(PROJECTS.PROJECT_ID.eq(PROCESS_QUEUE.PROJECT_ID))
                    .asField();

            return tx.select(orgId)
                    .from(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                    .fetchOne(orgId);
        }
    }

    @WithTimer
    public ProcessQueueEntry poll(Map<String, Object> capabilities) {
        while (true) {
            FindResult result = findEntry(capabilities);
            if (result.isDone()) {
                return result.item;
            }
        }
    }

    public List<ProcessEntry> list(ProcessFilter filter) {
        return list(filter, -1, -1);
    }

    public List<ProcessEntry> list(ProcessFilter filter, int limit, int offset) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectQuery<Record> query = buildSelect(tx, filter);

            boolean findAdjacentToDateRows = filter.beforeCreatedAt() == null && filter.beforeCreatedAt() != null;
            if (findAdjacentToDateRows) {
                query.addOrderBy(PROCESS_QUEUE.CREATED_AT.asc());
            } else {
                query.addOrderBy(PROCESS_QUEUE.CREATED_AT.desc());
            }

            if (limit > 0) {
                query.addLimit(limit);
            }

            if (offset >= 0) {
                query.addOffset(offset);
            }

            List<ProcessEntry> processEntries = query.fetch(this::toEntry);

            if (findAdjacentToDateRows) {
                Collections.reverse(processEntries);
            }

            return processEntries;
        }
    }

    public Map<ProcessStatus, Integer> getStatistics() {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROCESS_QUEUE.CURRENT_STATUS, count(asterisk())).from(PROCESS_QUEUE)
                    .groupBy(PROCESS_QUEUE.CURRENT_STATUS)
                    .fetchMap(r -> ProcessStatus.valueOf(r.value1()), Record2::value2);
        }
    }

    public List<ProcessStatusHistoryEntry> getHistory(ProcessKey processKey) {
        try (DSLContext tx = DSL.using(cfg)) {
            ProcessEvents pe = PROCESS_EVENTS.as("pe");
            return tx.select(historyEntryToJsonb(pe))
                    .from(pe)
                    .where(pe.INSTANCE_ID.eq(processKey.getInstanceId())
                            .and(pe.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                            .and(pe.EVENT_TYPE.eq(EventType.PROCESS_STATUS.name())))
                    .fetch(r -> objectMapper.deserialize(r.value1(), STATUS_HISTORY_ENTRY));

        }
    }

    public List<ProcessWaitHistoryEntry> getWaitHistory(ProcessKey processKey) {
        try (DSLContext tx = DSL.using(cfg)) {
            ProcessEvents pe = PROCESS_EVENTS.as("pe");
            return tx.select(waitEntryToJsonb(pe))
                    .from(pe)
                    .where(pe.INSTANCE_ID.eq(processKey.getInstanceId())
                            .and(pe.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                            .and(pe.EVENT_TYPE.eq(EventType.PROCESS_WAIT.name())))
                    .orderBy(pe.EVENT_DATE.desc())
                    .fetch(r -> objectMapper.deserialize(r.value1(), WAIT_HISTORY_ENTRY));

        }
    }

    private Field<Object> historyEntryToJsonb(ProcessEvents pe) {
        return function("jsonb_strip_nulls", Object.class,
                function("jsonb_build_object", Object.class,
                        inline("id"), pe.EVENT_ID,
                        inline("changeDate"), toJsonDate(pe.EVENT_DATE),
                        inline("status"), field("{0}->'status'", Object.class, pe.EVENT_DATA),
                        inline("payload"), field("{0} - 'status'", Object.class, pe.EVENT_DATA)));
    }

    private Field<Object> waitEntryToJsonb(ProcessEvents pe) {
        return function("jsonb_strip_nulls", Object.class,
                function("jsonb_build_object", Object.class,
                        inline("id"), pe.EVENT_ID,
                        inline("eventDate"), toJsonDate(pe.EVENT_DATE),
                        inline("type"), field("{0}->'type'", Object.class, pe.EVENT_DATA),
                        inline("reason"), field("{0}->'reason'", Object.class, pe.EVENT_DATA),
                        inline("payload"), field("{0} - 'type' - 'reason'", Object.class, pe.EVENT_DATA)));
    }

    private void filterByMetaFilters(SelectQuery<Record> query, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> e : filters.entrySet()) {
            query.addConditions(jsonText(PROCESS_QUEUE.META, e.getKey()).contains(e.getValue()));
        }
    }

    private void filterByTags(SelectQuery<Record> query, Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }

        String[] as = tags.toArray(new String[0]);
        query.addConditions(PgUtils.contains(PROCESS_QUEUE.PROCESS_TAGS, as));
    }

    public boolean exists(PartialProcessKey processKey) {
        UUID instanceId = processKey.getInstanceId();
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.fetchExists(tx.selectFrom(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId)));
        }
    }

    public void updateWait(ProcessKey key, AbstractWaitCondition waits) {
        tx(tx -> updateWait(tx, key, waits));
    }

    public void updateWait(DSLContext tx, ProcessKey key, AbstractWaitCondition waits) {
        tx.update(PROCESS_QUEUE)
                .set(PROCESS_QUEUE.WAIT_CONDITIONS, field("?::jsonb", String.class, objectMapper.serialize(waits)))
                .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp())
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(key.getInstanceId()))
                .execute();

        String eventData = objectMapper.serialize(waits != null ? waits : new NoneCondition());
        eventDao.insert(tx, key, EventType.PROCESS_WAIT.name(), eventData);
    }

    private FindResult findEntry(Map<String, Object> capabilities) {
        return txResult(tx -> {
            ProcessQueueEntry entry = nextEntry(tx, capabilities);
            if (entry == null) {
                return FindResult.notFound();
            }

            if (entry.projectId() != null) {
                boolean locked = queueLock.tryLock(tx);
                if (!locked) {
                    return FindResult.findNext();
                }

                for (ProcessQueueEntryFilter f : filters) {
                    if (!f.filter(tx, entry)) {
                        return FindResult.findNext();
                    }
                }
            }

            updateStatus(tx, entry.key(), ProcessStatus.STARTING);
            return FindResult.done(entry);
        });
    }

    private ProcessQueueEntry nextEntry(DSLContext tx, Map<String, Object> capabilities) {
        ProcessQueue q = PROCESS_QUEUE.as("q");

        Field<UUID> orgIdField = select(PROJECTS.ORG_ID).from(PROJECTS).where(PROJECTS.PROJECT_ID.eq(q.PROJECT_ID)).asField();

        SelectJoinStep<Record11<UUID, Timestamp, UUID, UUID, UUID, UUID, String, String, String, UUID, Boolean>> s =
                tx.select(
                        q.INSTANCE_ID,
                        q.CREATED_AT,
                        q.PROJECT_ID,
                        orgIdField,
                        q.INITIATOR_ID,
                        q.PARENT_INSTANCE_ID,
                        q.REPO_PATH,
                        q.REPO_URL,
                        q.COMMIT_ID,
                        q.REPO_ID,
                        q.IS_EXCLUSIVE)
                        .from(q);

        s.where(q.CURRENT_STATUS.eq(ProcessStatus.ENQUEUED.toString())
                .and(or(q.START_AT.isNull(),
                        q.START_AT.le(currentTimestamp())))
                .and(q.WAIT_CONDITIONS.isNull()));

        if (capabilities != null && !capabilities.isEmpty()) {
            Field<Object> agentReqField = field("{0}->'agent'", Object.class, q.REQUIREMENTS);
            Field<Object> capabilitiesField = field("?::jsonb", Object.class, value(objectMapper.serialize(capabilities)));
            s.where(q.REQUIREMENTS.isNull()
                    .or(field("{0} <@ {1}", Boolean.class, agentReqField, capabilitiesField)));
        }

        return s.orderBy(q.CREATED_AT)
                .limit(1)
                .forUpdate()
                .of(q)
                .skipLocked()
                .fetchOne(r -> ProcessQueueEntry.builder()
                        .key(new ProcessKey(r.value1(), r.value2()))
                        .projectId(r.value3())
                        .orgId(r.value4())
                        .initiatorId(r.value5())
                        .parentInstanceId(r.value6())
                        .repoPath(r.value7())
                        .repoUrl(r.value8())
                        .commitId(r.value9())
                        .repoId(r.value10())
                        .exclusive(r.value11())
                        .build());
    }

    private void insertStatusHistory(DSLContext tx, ProcessKey processKey, ProcessStatus status) {
        insertStatusHistory(tx, processKey, status, Collections.emptyMap());
    }

    private void insertStatusHistory(DSLContext tx, ProcessKey processKey, ProcessStatus status, Map<String, Object> statusPayload) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status.name());
        payload.putAll(statusPayload);

        eventDao.insert(tx, processKey, EventType.PROCESS_STATUS.name(), objectMapper.serialize(payload));
    }

    private void insertStatusHistory(DSLContext tx, List<ProcessKey> processKeys, ProcessStatus status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status.name());

        eventDao.insert(tx, processKeys, EventType.PROCESS_STATUS.name(), objectMapper.serialize(payload));
    }

    private SelectQuery<Record> buildSelect(DSLContext tx, ProcessFilter filter) {
        SelectQuery<Record> query = tx.selectQuery();
        query.addSelect(PROCESS_QUEUE.fields());
        query.addFrom(PROCESS_QUEUE);

        // users
        query.addSelect(USERS.USERNAME);
        query.addJoin(USERS, JoinType.LEFT_OUTER_JOIN, USERS.USER_ID.eq(PROCESS_QUEUE.INITIATOR_ID));

        // repositories
        query.addSelect(REPOSITORIES.REPO_NAME);
        query.addJoin(REPOSITORIES, JoinType.LEFT_OUTER_JOIN, REPOSITORIES.REPO_ID.eq(PROCESS_QUEUE.REPO_ID));

        // organizations
        Field<String> orgNameField = select(ORGANIZATIONS.ORG_NAME)
                .from(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_ID.eq(Tables.PROJECTS.ORG_ID)).asField(ORGANIZATIONS.ORG_NAME.getName());
        query.addSelect(orgNameField);

        // projects
        query.addSelect(Tables.PROJECTS.PROJECT_NAME, Tables.PROJECTS.ORG_ID);
        query.addJoin(Tables.PROJECTS, JoinType.LEFT_OUTER_JOIN, Tables.PROJECTS.PROJECT_ID.eq(PROCESS_QUEUE.PROJECT_ID));

        Set<UUID> orgIds = filter.orgIds();
        if (orgIds != null && !orgIds.isEmpty()) {
            if (filter.includeWithoutProject()) {
                query.addConditions(PROJECTS.ORG_ID.in(filter.orgIds()).or(PROCESS_QUEUE.PROJECT_ID.isNull()));
            } else {
                query.addConditions(PROJECTS.ORG_ID.in(filter.orgIds()));
            }
        }

        if (filter.projectId() != null) {
            if (filter.includeWithoutProject()) {
                query.addConditions(PROCESS_QUEUE.PROJECT_ID.eq(filter.projectId()).or(PROCESS_QUEUE.PROJECT_ID.isNull()));
            } else {
                query.addConditions(PROCESS_QUEUE.PROJECT_ID.eq(filter.projectId()));
            }
        }

        if (filter.initiator() != null) {
            query.addConditions(USERS.USERNAME.startsWith(filter.initiator()));
        }

        if (filter.afterCreatedAt() != null) {
            query.addConditions(PROCESS_QUEUE.CREATED_AT.greaterThan(filter.afterCreatedAt()));
        }

        if (filter.beforeCreatedAt() != null) {
            query.addConditions(PROCESS_QUEUE.CREATED_AT.lessThan(filter.beforeCreatedAt()));
        }

        if (filter.status() != null) {
            query.addConditions(PROCESS_QUEUE.CURRENT_STATUS.eq(filter.status().name()));
        }

        if (filter.parentId() != null) {
            query.addConditions(PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(filter.parentId()));
        }

        filterByMetaFilters(query, filter.metaFilters());

        filterByTags(query, filter.tags());

        Set<ProcessDataInclude> includes = filter.includes();

        if (includes.contains(ProcessDataInclude.CHILDREN_IDS)) {
            ProcessQueue pq = PROCESS_QUEUE.as("pq");
            SelectConditionStep<Record1<UUID>> childIds = DSL.select(pq.INSTANCE_ID)
                    .from(pq)
                    .where(pq.PARENT_INSTANCE_ID.eq(PROCESS_QUEUE.INSTANCE_ID));

            Field<UUID[]> childIdsField = DSL.field("array({0})", UUID[].class, childIds).as("children_ids");

            query.addSelect(childIdsField);
        }

        if (includes.contains(ProcessDataInclude.CHECKPOINTS)) {
            ProcessCheckpoints pc = PROCESS_CHECKPOINTS.as("pc");
            Field<Object> checkpoints = tx.select(
                    function("array_to_json", Object.class,
                            function("array_agg", Object.class,
                                    function("jsonb_strip_nulls", Object.class,
                                            function("jsonb_build_object", Object.class,
                                                    inline("id"), pc.CHECKPOINT_ID,
                                                    inline("name"), pc.CHECKPOINT_NAME,
                                                    inline("createdAt"), toJsonDate(pc.CHECKPOINT_DATE))))))
                    .from(pc)
                    .where(pc.INSTANCE_ID.eq(PROCESS_QUEUE.INSTANCE_ID)).asField("checkpoints");

            query.addSelect(checkpoints);
        }

        if (includes.contains(ProcessDataInclude.HISTORY)) {
            ProcessEvents pe = PROCESS_EVENTS.as("pe");
            Field<Object> history = tx.select(function("array_to_json", Object.class,
                    function("array_agg", Object.class, historyEntryToJsonb(pe))))
                    .from(pe)
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(pe.INSTANCE_ID)
                            .and(pe.EVENT_TYPE.eq(EventType.PROCESS_STATUS.name())
                                    .and(pe.INSTANCE_CREATED_AT.eq(PROCESS_QUEUE.CREATED_AT))))
                    .asField("status_history");
            query.addSelect(history);
        }

        return query;
    }

    private ProcessEntry get(DSLContext tx, PartialProcessKey key, Set<ProcessDataInclude> includes) {
        SelectQuery<Record> query = buildSelect(tx, ProcessFilter.builder()
                .includes(includes)
                .build());

        query.addConditions(PROCESS_QUEUE.INSTANCE_ID.eq(key.getInstanceId()));

        return query.fetchOne(this::toEntry);
    }

    private static Field<String> toJsonDate(Field<Timestamp> date) {
        return toChar(date, "YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"");
    }

    private ProcessEntry toEntry(Record r) {
        if (r == null) {
            return null;
        }

        ProcessKind kind;
        String s = r.get(PROCESS_QUEUE.PROCESS_KIND);
        if (s != null) {
            kind = ProcessKind.valueOf(s);
        } else {
            kind = ProcessKind.DEFAULT;
        }

        Set<String> tags = Collections.emptySet();
        String[] as = r.get(PROCESS_QUEUE.PROCESS_TAGS);
        if (as != null && as.length > 0) {
            tags = new HashSet<>(as.length);
            Collections.addAll(tags, as);
        }

        return ImmutableProcessEntry.builder()
                .instanceId(r.get(PROCESS_QUEUE.INSTANCE_ID))
                .kind(kind)
                .parentInstanceId(r.get(PROCESS_QUEUE.PARENT_INSTANCE_ID))
                .orgId(r.get(Tables.PROJECTS.ORG_ID))
                .orgName(r.get(ORGANIZATIONS.ORG_NAME))
                .projectId(r.get(PROCESS_QUEUE.PROJECT_ID))
                .projectName(r.get(Tables.PROJECTS.PROJECT_NAME))
                .repoId(r.get(PROCESS_QUEUE.REPO_ID))
                .repoName(r.get(REPOSITORIES.REPO_NAME))
                .repoUrl(r.get(PROCESS_QUEUE.REPO_URL))
                .repoPath(r.get(PROCESS_QUEUE.REPO_PATH))
                .commitId(r.get(PROCESS_QUEUE.COMMIT_ID))
                .commitMsg(r.get(PROCESS_QUEUE.COMMIT_MSG))
                .initiator(r.get(USERS.USERNAME))
                .initiatorId(r.get(PROCESS_QUEUE.INITIATOR_ID))
                .lastUpdatedAt(r.get(PROCESS_QUEUE.LAST_UPDATED_AT))
                .createdAt(r.get(PROCESS_QUEUE.CREATED_AT))
                .status(ProcessStatus.valueOf(r.get(PROCESS_QUEUE.CURRENT_STATUS)))
                .lastAgentId(r.get(PROCESS_QUEUE.LAST_AGENT_ID))
                .tags(tags)
                .childrenIds(toSet(getOrNull(r, "children_ids")))
                .meta(objectMapper.deserialize(r.get(PROCESS_QUEUE.META)))
                .handlers(toSet(r.get(PROCESS_QUEUE.HANDLERS)))
                .logFileName(r.get(PROCESS_QUEUE.INSTANCE_ID) + ".log")
                .checkpoints(objectMapper.deserialize(getOrNull(r, "checkpoints"), LIST_OF_CHECKPOINTS))
                .statusHistory(objectMapper.deserialize(getOrNull(r, "status_history"), LIST_OF_STATUS_HISTORY))
                .build();
    }

    @SuppressWarnings("unchecked")
    private <E> E getOrNull(Record r, String fieldName) {
        Field<?> field = r.field(fieldName);
        if (field == null) {
            return null;
        }

        return (E) r.get(field);
    }

    private static <E> Set<E> toSet(E[] arr) {
        if (arr == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.asList(arr));
    }

    private static String[] toArray(Set<String> s) {
        if (s == null || s.isEmpty()) {
            return new String[0];
        }

        return s.toArray(new String[0]);
    }

    public static class IdAndStatus {

        private final ProcessKey processKey;
        private final ProcessStatus status;

        public IdAndStatus(ProcessKey processKey, ProcessStatus status) {
            this.processKey = processKey;
            this.status = status;
        }

        public ProcessKey getProcessKey() {
            return processKey;
        }

        public ProcessStatus getStatus() {
            return status;
        }
    }

    private static class FindResult {

        enum Status {
            DONE,
            NEXT_ITEM
        }

        private final Status status;
        private final ProcessQueueEntry item;

        private FindResult(Status status, ProcessQueueEntry item) {
            this.status = status;
            this.item = item;
        }

        private boolean isDone() {
            return status == Status.DONE;
        }

        public static FindResult done(ProcessQueueEntry item) {
            return new FindResult(Status.DONE, item);
        }

        static FindResult notFound() {
            return done(null);
        }

        static FindResult findNext() {
            return new FindResult(Status.NEXT_ITEM, null);
        }
    }
}
