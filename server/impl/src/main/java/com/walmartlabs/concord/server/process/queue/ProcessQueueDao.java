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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.sdk.EventType;
import com.walmartlabs.concord.server.jooq.Tables;
import com.walmartlabs.concord.server.jooq.tables.ProcessCheckpoints;
import com.walmartlabs.concord.server.jooq.tables.ProcessEvents;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessQueueRecord;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.policy.PolicyDao;
import com.walmartlabs.concord.server.org.policy.PolicyRules;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.process.event.EventDao;
import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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

    private static final Logger log = LoggerFactory.getLogger(ProcessQueueDao.class);

    private static final List<ProcessStatus> RUNNING_PROCESS_STATUSES = Arrays.asList(
            ProcessStatus.STARTING,
            ProcessStatus.RUNNING,
            ProcessStatus.RESUMING);

    private static final Set<ProcessDataInclude> DEFAULT_INCLUDES = Collections.singleton(ProcessDataInclude.CHILDREN_IDS);

    private static final TypeReference<List<ProcessEntry.Checkpoint>> LIST_OF_CHECKPOINTS = new TypeReference<List<ProcessEntry.Checkpoint>>() {};
    private static final TypeReference<List<ProcessEntry.StatusHistory>> LIST_OF_STATUS_HISTORY = new TypeReference<List<ProcessEntry.StatusHistory>>() {};

    private final PolicyDao policyDao;
    private final EventDao eventDao;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    protected ProcessQueueDao(@Named("app") Configuration cfg, PolicyDao policyDao, EventDao eventDao) {
        super(cfg);
        this.policyDao = policyDao;
        this.eventDao = eventDao;
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
                        field("?::jsonb", serialize(meta)))
                .execute();

        insertHistoryStatus(tx, processKey, ProcessStatus.PREPARING);
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

        insertHistoryStatus(tx, processKey, status);
    }

    private static Field<Timestamp> createRunningAtValue(ProcessStatus status) {
        return when(PROCESS_QUEUE.CURRENT_STATUS.eq(ProcessStatus.RUNNING.toString()), PROCESS_QUEUE.LAST_RUN_AT)
                .otherwise(when(value(status.toString()).eq(ProcessStatus.RUNNING.toString()), currentTimestamp())
                        .otherwise(PROCESS_QUEUE.LAST_RUN_AT));
    }

    public void enqueue(ProcessKey processKey, Set<String> tags, Instant startAt,
                        Map<String, Object> requirements, UUID repoId, String repoUrl, String repoPath,
                        String commitId, String commitMsg, Long processTimeout,
                        Set<String> handlers, Map<String, Object> meta) {

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
                q.set(PROCESS_QUEUE.REQUIREMENTS, field("?::jsonb", String.class, serialize(requirements)));
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
                q.set(PROCESS_QUEUE.META, field(coalesce(PROCESS_QUEUE.META, field("?::jsonb", String.class, "{}")) + " || ?::jsonb", String.class, serialize(meta)));
            }

            int i = q
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(processKey.getInstanceId()))
                    .execute();

            if (i != 1) {
                throw new DataAccessException("Invalid number of rows updated: " + i);
            }

            insertHistoryStatus(tx, processKey, ProcessStatus.ENQUEUED);
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

        insertHistoryStatus(tx, processKey, status, statusPayload);
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

            insertHistoryStatus(tx, processKey, status);

            return i == 1;
        });
    }

    public boolean updateMeta(PartialProcessKey processKey, Map<String, Object> meta) {
        UUID instanceId = processKey.getInstanceId();

        return txResult(tx -> {
            int i = tx.update(PROCESS_QUEUE)
                    .set(PROCESS_QUEUE.META, field(coalesce(PROCESS_QUEUE.META, field("?::jsonb", String.class, "{}")) + " || ?::jsonb", String.class, serialize(meta)))
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

            insertHistoryStatus(tx, processKeys, status);

            return i == processKeys.size();
        });
    }

    private void insertHistoryStatus(DSLContext tx, ProcessKey processKey, ProcessStatus status) {
        insertHistoryStatus(tx, processKey, status, Collections.emptyMap());
    }

    private void insertHistoryStatus(DSLContext tx, ProcessKey processKey, ProcessStatus status, Map<String, Object> statusPayload) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status.name());
        payload.putAll(statusPayload);

        try {
            eventDao.insert(tx, processKey, EventType.PROCESS_STATUS.name(), objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void insertHistoryStatus(DSLContext tx, List<ProcessKey> processKeys, ProcessStatus status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status.name());

        try {
            eventDao.insert(tx, processKeys, EventType.PROCESS_STATUS.name(), objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
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

            SelectQuery<Record> query = buildSelect(tx, DEFAULT_INCLUDES);

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
    public ProcessItem poll(Map<String, Object> capabilities) {
        Set<UUID> excludeProjects = new HashSet<>();
        while (true) {
            FindResult result = findEntry(capabilities, excludeProjects);
            if (result.isDone()) {
                return result.item;
            } else {
                excludeProjects.add(result.excludeProject);
            }
        }
    }

    public List<ProcessEntry> list(ProcessFilter filter, int limit, int offset) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectQuery<Record> query = buildSelect(tx, DEFAULT_INCLUDES);

            Set<UUID> orgIds = filter.orgIds();
            if (orgIds != null && !orgIds.isEmpty()) {
                SelectConditionStep<Record1<UUID>> projectIds = select(PROJECTS.PROJECT_ID)
                        .from(PROJECTS)
                        .where(PROJECTS.ORG_ID.in(orgIds));

                if (filter.includeWithoutProjects()) {
                    query.addConditions(PROCESS_QUEUE.PROJECT_ID.in(projectIds)
                            .or(PROCESS_QUEUE.PROJECT_ID.isNull()));
                } else {
                    query.addConditions(PROCESS_QUEUE.PROJECT_ID.in(projectIds));
                }
            }

            if (filter.projectId() != null) {
                query.addConditions(PROCESS_QUEUE.PROJECT_ID.eq(filter.projectId()));
            }

            if (filter.afterCreatedAt() != null) {
                query.addConditions(PROCESS_QUEUE.CREATED_AT.greaterThan(filter.afterCreatedAt()));
            }

            if (filter.beforeCreatedAt() != null) {
                query.addConditions(PROCESS_QUEUE.CREATED_AT.lessThan(filter.beforeCreatedAt()));
            }

            if (filter.initiator() != null) {
                query.addConditions(USERS.USERNAME.startsWith(filter.initiator()));
            }

            ProcessStatus status = filter.status();
            if (status != null) {
                query.addConditions(PROCESS_QUEUE.CURRENT_STATUS.eq(status.name()));
            }

            if (filter.parentId() != null) {
                query.addConditions(PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(filter.parentId()));
            }

            filterByMetaFilters(query, filter.metaFilters());

            filterByTags(query, filter.tags());

            boolean findAdjacentToDateRows = filter.beforeCreatedAt() == null && filter.beforeCreatedAt() != null;
            if (findAdjacentToDateRows) {
                query.addOrderBy(PROCESS_QUEUE.CREATED_AT.asc());
            } else {
                query.addOrderBy(PROCESS_QUEUE.CREATED_AT.desc());
            }

            query.addLimit(limit);
            query.addOffset(offset);

            List<ProcessEntry> processEntries = query.fetch(this::toEntry);

            if (findAdjacentToDateRows) {
                Collections.reverse(processEntries);
            }

            return processEntries;
        }
    }

    public List<ProcessEntry> list(UUID parentInstanceId, Set<String> tags) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectQuery<Record> query = buildSelect(tx, DEFAULT_INCLUDES);
            query.addConditions(PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(parentInstanceId));

            filterByTags(query, tags);

            query.addOrderBy(PROCESS_QUEUE.CREATED_AT.desc());
            return query.fetch(this::toEntry);
        }
    }

    public Map<ProcessStatus, Integer> getStatistics() {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROCESS_QUEUE.CURRENT_STATUS, count(asterisk())).from(PROCESS_QUEUE)
                    .groupBy(PROCESS_QUEUE.CURRENT_STATUS)
                    .fetchMap(r -> ProcessStatus.valueOf(r.value1()), r -> r.value2());
        }
    }

    private void filterByMetaFilters(SelectQuery<Record> query, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> e : filters.entrySet()) {
            query.addConditions(jsonText(PROCESS_QUEUE.META, e.getKey()).contains(e.getValue()));
        }
    }

    private static Field<String> jsonText(Field<?> field, String name) {
        return field("{0}::jsonb->>{1}", Object.class, field, inline(name)).cast(String.class);
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

    private FindResult findEntry(Map<String, Object> capabilities, Set<UUID> excludeProjects) {
        return txResult(tx -> {
            Record10<UUID, Timestamp, UUID, UUID, UUID, UUID, String, String, String, UUID> r = nextItem(tx, capabilities, excludeProjects);
            if (r == null) {
                return FindResult.notFound();
            }

            UUID instanceId = r.value1();
            Timestamp createdAt = r.value2();
            UUID projectId = r.value3();
            UUID orgId = r.value4();
            UUID userId = r.value5();
            UUID parentInstanceId = r.value6();
            String repoPath = r.value7();
            String repoUrl = r.value8();
            String commitId = r.value9();
            UUID repoId = r.value10();

            PolicyEngine pe = getPolicyEngine(tx, orgId, projectId, userId, parentInstanceId);
            if (pe != null) {
                boolean locked = tryLock(tx, projectId);
                if (locked) {
                    int count = countRunning(tx, projectId);
                    if (!pe.getConcurrentProcessPolicy().check(count).getDeny().isEmpty()) {
                        log.debug("findEntry ['{}'] -> {} running", projectId, count);
                        return FindResult.findNext(projectId);
                    }
                } else {
                    log.debug("findEntry ['{}'] -> already locked", projectId);
                    return FindResult.findNext(projectId);
                }
            }

            ProcessKey processKey = new ProcessKey(instanceId, createdAt);
            updateStatus(tx, processKey, ProcessStatus.STARTING);
            return FindResult.done(new ProcessItem(processKey, orgId, repoId, repoPath, repoUrl, commitId));
        });
    }

    private boolean tryLock(DSLContext tx, UUID projectId) throws SQLException {
        String sql = "{ ? = call pg_try_advisory_xact_lock(?) }";

        return tx.connectionResult(conn -> {
            try (CallableStatement cs = conn.prepareCall(sql)) {
                cs.registerOutParameter(1, Types.BOOLEAN);
                cs.setLong(2, projectId.getLeastSignificantBits() ^ projectId.getMostSignificantBits());
                cs.execute();
                return cs.getBoolean(1);
            }
        });
    }

    private PolicyEngine getPolicyEngine(DSLContext tx, UUID orgId, UUID prjId, UUID userId, UUID parentInstanceId) {
        if (prjId == null) {
            return null;
        }

        if (parentInstanceId != null) {
            return null;
        }

        PolicyRules policy = policyDao.getRules(tx, orgId, prjId, userId);
        if (policy == null) {
            return null;
        }

        PolicyEngine pe = new PolicyEngine(policy.rules());
        return pe.getConcurrentProcessPolicy().hasRule() ? pe : null;
    }

    private Record10<UUID, Timestamp, UUID, UUID, UUID, UUID, String, String, String, UUID> nextItem(DSLContext tx, Map<String, Object> capabilities, Set<UUID> excludeProjectIds) {
        ProcessQueue q = PROCESS_QUEUE.as("q");

        Field<UUID> orgIdField = select(PROJECTS.ORG_ID).from(PROJECTS).where(PROJECTS.PROJECT_ID.eq(q.PROJECT_ID)).asField();

        SelectJoinStep<Record10<UUID, Timestamp, UUID, UUID, UUID, UUID, String, String, String, UUID>> s =
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
                        q.REPO_ID)
                        .from(q);

        s.where(q.CURRENT_STATUS.eq(ProcessStatus.ENQUEUED.toString())
                .and(or(q.START_AT.isNull(),
                        q.START_AT.le(currentTimestamp()))));

        if (capabilities != null && !capabilities.isEmpty()) {
            Field<Object> agentReqField = field("{0}->'agent'", Object.class, q.REQUIREMENTS);
            Field<Object> capabilitiesField = field("?::jsonb", Object.class, value(serialize(capabilities)));
            s.where(q.REQUIREMENTS.isNull()
                    .or(field("{0} <@ {1}", Boolean.class, agentReqField, capabilitiesField)));
        }

        if (!excludeProjectIds.isEmpty()) {
            s.where(q.PROJECT_ID.isNull().or(q.PROJECT_ID.notIn(excludeProjectIds)));
        }

        return s.orderBy(q.CREATED_AT)
                .limit(1)
                .forUpdate()
                .of(q)
                .skipLocked()
                .fetchOne();
    }

    private int countRunning(DSLContext tx, UUID prjId) {
        return tx.selectCount()
                .from(PROCESS_QUEUE)
                .where(PROCESS_QUEUE.PROJECT_ID.eq(prjId).and(PROCESS_QUEUE.CURRENT_STATUS.in(RUNNING_PROCESS_STATUSES)))
                .fetchOne(0, int.class);
    }

    private String serialize(Object details) {
        if (details == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(details);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> deserialize(Object o) {
        return deserialize(o, new TypeReference<Map<String, Object>>() {
        });
    }

    private <T> T deserialize(Object o, TypeReference valueTypeRef) {
        if (o == null) {
            return null;
        }

        try {
            return objectMapper.readValue(String.valueOf(o), valueTypeRef);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SelectQuery<Record> buildSelect(DSLContext tx, Set<ProcessDataInclude> includes) {
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
            Field<Object> history = tx.select(
                    function("array_to_json", Object.class,
                            function("array_agg", Object.class,
                                    function("jsonb_strip_nulls", Object.class,
                                            function("jsonb_build_object", Object.class,
                                                    inline("changeDate"), toJsonDate(pe.EVENT_DATE),
                                                    inline("status"), field("{0}->'status'", Object.class, pe.EVENT_DATA),
                                                    inline("checkpointId"), field("{0}->'checkpointId'", Object.class, pe.EVENT_DATA))))))
                    .from(pe)
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(pe.INSTANCE_ID).and(pe.EVENT_TYPE.eq(EventType.PROCESS_STATUS.name()).and(pe.EVENT_DATE.greaterOrEqual(PROCESS_QUEUE.CREATED_AT))))
                    .asField("status_history");
            query.addSelect(history);
        }

        return query;
    }

    private ProcessEntry get(DSLContext tx, PartialProcessKey key, Set<ProcessDataInclude> includes) {
        SelectQuery<Record> query = buildSelect(tx, includes);
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
                .meta(deserialize(r.get(PROCESS_QUEUE.META)))
                .handlers(toSet(r.get(PROCESS_QUEUE.HANDLERS)))
                .logFileName(r.get(PROCESS_QUEUE.INSTANCE_ID) + ".log")
                .checkpoints(deserialize(getOrNull(r, "checkpoints"), LIST_OF_CHECKPOINTS))
                .statusHistory(deserialize(getOrNull(r, "status_history"), LIST_OF_STATUS_HISTORY))
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
            SKIP
        }

        private final Status status;
        private final ProcessItem item;
        private final UUID excludeProject;

        private FindResult(Status status, ProcessItem item, UUID excludeProject) {
            this.status = status;
            this.item = item;
            this.excludeProject = excludeProject;
        }

        private boolean isDone() {
            return status == Status.DONE;
        }

        public static FindResult done(ProcessItem item) {
            return new FindResult(Status.DONE, item, null);
        }

        static FindResult notFound() {
            return done(null);
        }

        static FindResult findNext(UUID excludeProject) {
            return new FindResult(Status.SKIP, null, excludeProject);
        }
    }

    public static class ProcessItem {

        private final ProcessKey key;
        private final UUID orgId;
        private final UUID repoId;
        private final String repoPath;
        private final String repoUrl;
        private final String commitId;

        public ProcessItem(ProcessKey key, UUID orgId, UUID repoId, String repoPath, String repoUrl, String commitId) {
            this.key = key;
            this.orgId = orgId;
            this.repoId = repoId;
            this.repoPath = repoPath;
            this.repoUrl = repoUrl;
            this.commitId = commitId;
        }

        public ProcessKey getKey() {
            return key;
        }

        public UUID getOrgId() {
            return orgId;
        }

        public UUID getRepoId() {
            return repoId;
        }

        public String getRepoPath() {
            return repoPath;
        }

        public String getRepoUrl() {
            return repoUrl;
        }

        public String getCommitId() {
            return commitId;
        }
    }
}
