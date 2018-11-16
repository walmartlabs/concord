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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.sdk.EventType;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessQueueRecord;
import com.walmartlabs.concord.server.jooq.tables.records.VProcessQueueRecord;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.policy.PolicyDao;
import com.walmartlabs.concord.server.org.policy.PolicyEntry;
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
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.VProcessQueue.V_PROCESS_QUEUE;
import static org.jooq.impl.DSL.*;

@Named
public class ProcessQueueDao extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(ProcessQueueDao.class);

    private static final List<ProcessStatus> RUNNING_PROCESS_STATUSES = Arrays.asList(
            ProcessStatus.STARTING,
            ProcessStatus.RUNNING,
            ProcessStatus.RESUMING);

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
        // TODO this is a good candidate for caching, since both instanceId and createdAt are immutable
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

    public void insertInitial(DSLContext tx, ProcessKey processKey, ProcessKind kind, UUID parentInstanceId,
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

    public void updateAgentId(PartialProcessKey processKey, String agentId, ProcessStatus status) {
        tx(tx -> updateAgentId(tx, processKey, agentId, status));
    }

    public void updateAgentId(DSLContext tx, PartialProcessKey processKey, String agentId, ProcessStatus status) {
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

    public void enqueue(PartialProcessKey processKey, Set<String> tags, Instant startAt,
                        Map<String, Object> requirements, UUID repoId, String repoUrl, String repoPath,
                        String commitId, String commitMsg, Long processTimeout,
                        Set<String> handlers) {

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

            int i = q
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(processKey.getInstanceId()))
                    .execute();

            if (i != 1) {
                throw new DataAccessException("Invalid number of rows updated: " + i);
            }

            insertHistoryStatus(tx, processKey, ProcessStatus.ENQUEUED);
        });
    }

    public void updateStatus(PartialProcessKey processKey, ProcessStatus status) {
        updateStatus(processKey, status, Collections.emptyMap());
    }

    public void updateStatus(PartialProcessKey processKey, ProcessStatus status, Map<String, Object> statusPayload) {
        tx(tx -> updateStatus(tx, processKey, status, statusPayload));
    }

    public void updateStatus(DSLContext tx, PartialProcessKey processKey, ProcessStatus status) {
        updateStatus(tx, processKey, status, Collections.emptyMap());
    }

    public void updateStatus(DSLContext tx, PartialProcessKey processKey, ProcessStatus status, Map<String, Object> statusPayload) {
        UUID instanceId = processKey.getInstanceId();

        tx.update(PROCESS_QUEUE)
                .set(PROCESS_QUEUE.CURRENT_STATUS, status.toString())
                .set(PROCESS_QUEUE.LAST_RUN_AT, createRunningAtValue(status))
                .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp())
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                .execute();

        insertHistoryStatus(tx, processKey, status, statusPayload);
    }

    public boolean updateStatus(PartialProcessKey processKey, ProcessStatus expected, ProcessStatus status) {
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

    public boolean updateStatus(List<PartialProcessKey> processKeys, ProcessStatus status, List<ProcessStatus> expected) {
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

    private void insertHistoryStatus(DSLContext tx, PartialProcessKey processKey, ProcessStatus status) {
        insertHistoryStatus(tx, processKey, status, Collections.emptyMap());
    }

    private void insertHistoryStatus(DSLContext tx, PartialProcessKey processKey, ProcessStatus status, Map<String, Object> statusPayload)  {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status.name());
        payload.putAll(statusPayload);

        try {
            eventDao.insert(tx, processKey.getInstanceId(), EventType.PROCESS_STATUS.name(), objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void insertHistoryStatus(DSLContext tx, List<PartialProcessKey> processKeys, ProcessStatus status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status.name());

        try {
            eventDao.insert(tx, processKeys.stream().map(PartialProcessKey::getInstanceId).collect(Collectors.toList()),
                    EventType.PROCESS_STATUS.name(), objectMapper.writeValueAsString(payload));
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
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.selectFrom(V_PROCESS_QUEUE)
                    .where(V_PROCESS_QUEUE.INSTANCE_ID.eq(processKey.getInstanceId()))
                    .orderBy(V_PROCESS_QUEUE.CREATED_AT.desc())
                    .fetchOne(this::toEntry);
        }
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

            List<VProcessQueueRecord> r = tx.selectFrom(V_PROCESS_QUEUE)
                    .where(V_PROCESS_QUEUE.INSTANCE_ID.in(instanceIds))
                    .fetch();

            if (r == null) {
                return null;
            }

            return toEntryList(r);
        }
    }

    public List<IdAndStatus> getCascade(PartialProcessKey parentKey) {
        UUID parentInstanceId = parentKey.getInstanceId();

        try (DSLContext tx = DSL.using(cfg)) {
            return tx.withRecursive("children").as(
                    select(V_PROCESS_QUEUE.INSTANCE_ID, V_PROCESS_QUEUE.CURRENT_STATUS).from(V_PROCESS_QUEUE)
                            .where(V_PROCESS_QUEUE.INSTANCE_ID.eq(parentInstanceId))
                            .unionAll(
                                    select(V_PROCESS_QUEUE.INSTANCE_ID, V_PROCESS_QUEUE.CURRENT_STATUS).from(V_PROCESS_QUEUE)
                                            .join(name("children"))
                                            .on(V_PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(
                                                    field(name("children", "INSTANCE_ID"), UUID.class)))))
                    .select()
                    .from(name("children"))
                    .fetch(r -> new IdAndStatus(PartialProcessKey.from(r.get(0, UUID.class)),
                            ProcessStatus.valueOf(r.get(1, String.class))));
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
    public ProcessKey poll(Map<String, Object> capabilities) {
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
            SelectWhereStep<VProcessQueueRecord> s = tx.selectFrom(V_PROCESS_QUEUE);

            if (filter.getOrgIds() != null && !filter.getOrgIds().isEmpty()) {
                SelectConditionStep<Record1<UUID>> projectIds = select(PROJECTS.PROJECT_ID)
                        .from(PROJECTS)
                        .where(PROJECTS.ORG_ID.in(filter.getOrgIds()));

                if (filter.isIncludeWoProjects()) {
                    s.where(V_PROCESS_QUEUE.PROJECT_ID.in(projectIds)
                            .or(V_PROCESS_QUEUE.PROJECT_ID.isNull()));
                } else {
                    s.where(V_PROCESS_QUEUE.PROJECT_ID.in(projectIds));
                }
            }

            if (filter.getProjectId() != null) {
                s.where(V_PROCESS_QUEUE.PROJECT_ID.eq(filter.getProjectId()));
            }

            if (filter.getAfterCreatedAt() != null) {
                s.where(V_PROCESS_QUEUE.CREATED_AT.greaterThan(filter.getAfterCreatedAt()));
            }

            if (filter.getBeforeCreatedAt() != null) {
                s.where(V_PROCESS_QUEUE.CREATED_AT.lessThan(filter.getBeforeCreatedAt()));
            }

            if (filter.getInitiator() != null) {
                s.where(V_PROCESS_QUEUE.INITIATOR.startsWith(filter.getInitiator()));
            }

            if (filter.getProcessStatus() != null) {
                s.where(V_PROCESS_QUEUE.CURRENT_STATUS.eq(filter.getProcessStatus().name()));
            }

            if (filter.getParentId() != null) {
                s.where(V_PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(filter.getParentId()));
            }

            filterByTags(s, filter.getTags());

            boolean findAdjacentToDateRows = filter.getBeforeCreatedAt() == null && filter.getAfterCreatedAt() != null;
            if (findAdjacentToDateRows) {
                s.orderBy(V_PROCESS_QUEUE.CREATED_AT.asc());
            } else {
                s.orderBy(V_PROCESS_QUEUE.CREATED_AT.desc());
            }

            List<ProcessEntry> processEntries = s.limit(limit).offset(offset).fetch(this::toEntry);

            if (findAdjacentToDateRows) {
                Collections.reverse(processEntries);
            }

            return processEntries;
        }
    }

    public List<ProcessEntry> list(UUID parentInstanceId, Set<String> tags) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectConditionStep<VProcessQueueRecord> s = tx.selectFrom(V_PROCESS_QUEUE)
                    .where(V_PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(parentInstanceId));

            filterByTags(s, tags);

            return s.orderBy(V_PROCESS_QUEUE.CREATED_AT.desc())
                    .fetch(this::toEntry);
        }
    }

    public Map<ProcessStatus, Integer> getStatistics() {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROCESS_QUEUE.CURRENT_STATUS, count(asterisk())).from(PROCESS_QUEUE)
                    .groupBy(PROCESS_QUEUE.CURRENT_STATUS)
                    .fetchMap(r -> ProcessStatus.valueOf(r.value1()), r -> r.value2());
        }
    }

    private Select<VProcessQueueRecord> filterByTags(SelectWhereStep<VProcessQueueRecord> q, Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return q;
        }

        String[] as = tags.toArray(new String[0]);
        return q.where(PgUtils.contains(V_PROCESS_QUEUE.PROCESS_TAGS, as));
    }

    private SelectConditionStep<VProcessQueueRecord> filterByTags(SelectConditionStep<VProcessQueueRecord> q, Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return q;
        }

        String[] as = tags.toArray(new String[0]);
        return q.and(PgUtils.contains(V_PROCESS_QUEUE.PROCESS_TAGS, as));
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
            Record6<UUID, Timestamp, UUID, UUID, UUID, UUID> r = nextItem(tx, capabilities, excludeProjects);
            if (r == null) {
                return FindResult.notFound();
            }

            UUID instanceId = r.value1();
            Timestamp createdAt = r.value2();
            UUID projectId = r.value3();
            UUID orgId = r.value4();
            UUID userId = r.value5();
            UUID parentInstanceId = r.value6();

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

            PartialProcessKey processKey = PartialProcessKey.from(instanceId);
            updateStatus(tx, processKey, ProcessStatus.STARTING);

            return FindResult.done(new ProcessKey(instanceId, createdAt));
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

        PolicyEntry policy = policyDao.getLinked(tx, orgId, prjId, userId);
        if (policy == null) {
            return null;
        }

        PolicyEngine pe = new PolicyEngine(policy.getRules());
        return pe.getConcurrentProcessPolicy().hasRule() ? pe : null;
    }

    private Record6<UUID, Timestamp, UUID, UUID, UUID, UUID> nextItem(DSLContext tx, Map<String, Object> capabilities, Set<UUID> excludeProjectIds) {
        ProcessQueue q = PROCESS_QUEUE.as("q");

        Field<UUID> orgIdField = select(PROJECTS.ORG_ID).from(PROJECTS).where(PROJECTS.PROJECT_ID.eq(q.PROJECT_ID)).asField();

        SelectJoinStep<Record6<UUID, Timestamp, UUID, UUID, UUID, UUID>> s = tx.select(q.INSTANCE_ID, q.CREATED_AT, q.PROJECT_ID, orgIdField, q.INITIATOR_ID, q.PARENT_INSTANCE_ID)
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserialize(String o) {
        if (o == null) {
            return null;
        }

        try {
            return objectMapper.readValue(o, Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ProcessEntry toEntry(VProcessQueueRecord r) {
        ProcessKind kind;
        String s = r.getProcessKind();
        if (s != null) {
            kind = ProcessKind.valueOf(s);
        } else {
            kind = ProcessKind.DEFAULT;
        }

        Set<String> tags = null;
        String[] as = r.getProcessTags();
        if (as != null && as.length > 0) {
            tags = new HashSet<>(as.length);
            Collections.addAll(tags, as);
        }

        return new ProcessEntry(r.getInstanceId(),
                kind,
                r.getParentInstanceId(),
                r.getOrgId(),
                r.getOrgName(),
                r.getProjectId(),
                r.getProjectName(),
                r.getRepoId(),
                r.getRepoName(),
                r.getRepoUrl(),
                r.getRepoPath(),
                r.getCommitId(),
                r.getCommitMsg(),
                r.getCreatedAt(),
                r.getInitiator(),
                r.getInitiatorId(),
                r.getLastUpdatedAt(),
                ProcessStatus.valueOf(r.getCurrentStatus()),
                r.getLastAgentId(),
                tags,
                toSet(r.getChildrenIds()),
                deserialize(r.getMeta()),
                toSet(r.getHandlers()));
    }

    private static Set<UUID> toSet(UUID[] arr) {
        if (arr == null) {
            return null;
        }
        return new HashSet<>(Arrays.asList(arr));
    }

    private static Set<String> toSet(String[] arr) {
        if (arr == null) {
            return null;
        }
        return new HashSet<>(Arrays.asList(arr));
    }

    private List<ProcessEntry> toEntryList(List<VProcessQueueRecord> records) {
        return records.stream()
                .map(this::toEntry)
                .collect(Collectors.toList());
    }

    private static String[] toArray(Set<String> s) {
        if (s == null || s.isEmpty()) {
            return new String[0];
        }

        return s.toArray(new String[0]);
    }

    public static class IdAndStatus {

        private final PartialProcessKey processKey;
        private final ProcessStatus status;

        public IdAndStatus(PartialProcessKey processKey, ProcessStatus status) {
            this.processKey = processKey;
            this.status = status;
        }

        public PartialProcessKey getProcessKey() {
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
        private final ProcessKey item;
        private final UUID excludeProject;

        private FindResult(Status status, ProcessKey item, UUID excludeProject) {
            this.status = status;
            this.item = item;
            this.excludeProject = excludeProject;
        }

        private boolean isDone() {
            return status == Status.DONE;
        }

        public static FindResult done(ProcessKey item) {
            return new FindResult(Status.DONE, item, null);
        }

        static FindResult notFound() {
            return done(null);
        }

        static FindResult findNext(UUID excludeProject) {
            return new FindResult(Status.SKIP, null, excludeProject);
        }
    }
}
