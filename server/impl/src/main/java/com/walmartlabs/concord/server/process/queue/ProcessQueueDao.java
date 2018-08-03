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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessQueueRecord;
import com.walmartlabs.concord.server.jooq.tables.records.VProcessQueueRecord;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.ProcessKind;
import com.walmartlabs.concord.server.process.ProcessStatus;
import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.VProcessQueue.V_PROCESS_QUEUE;
import static org.jooq.impl.DSL.*;

@Named
public class ProcessQueueDao extends AbstractDao {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    protected ProcessQueueDao(@Named("app") Configuration cfg) {
        super(cfg);
    }

    public void insertInitial(UUID instanceId, ProcessKind kind, UUID parentInstanceId,
                              UUID projectId, String initiator) {

        tx(tx -> insertInitial(tx, instanceId, kind, parentInstanceId, projectId, initiator));
    }

    public void insertInitial(DSLContext tx, UUID instanceId, ProcessKind kind, UUID parentInstanceId,
                              UUID projectId, String initiator) {

        tx.insertInto(PROCESS_QUEUE)
                .columns(PROCESS_QUEUE.INSTANCE_ID,
                        PROCESS_QUEUE.PROCESS_KIND,
                        PROCESS_QUEUE.PARENT_INSTANCE_ID,
                        PROCESS_QUEUE.PROJECT_ID,
                        PROCESS_QUEUE.CREATED_AT,
                        PROCESS_QUEUE.INITIATOR,
                        PROCESS_QUEUE.CURRENT_STATUS,
                        PROCESS_QUEUE.LAST_UPDATED_AT)
                .values(value(instanceId),
                        value(kind.toString()),
                        value(parentInstanceId),
                        value(projectId),
                        currentTimestamp(),
                        value(initiator),
                        value(ProcessStatus.PREPARING.toString()),
                        currentTimestamp())
                .execute();
    }

    public void updateAgentId(UUID instanceId, String agentId, ProcessStatus status) {
        tx(tx -> updateAgentId(tx, instanceId, agentId, status));
    }

    public void updateAgentId(DSLContext tx, UUID instanceId, String agentId, ProcessStatus status) {
        int i = tx.update(PROCESS_QUEUE)
                .set(PROCESS_QUEUE.CURRENT_STATUS, status.toString())
                .set(PROCESS_QUEUE.LAST_AGENT_ID, agentId)
                .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp())
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                .execute();

        if (i != 1) {
            throw new DataAccessException("Invalid number of rows updated: " + i);
        }
    }

    public void update(UUID instanceId, ProcessStatus status) {
        update(instanceId, status, null, null, null, null, null, null, null, null);
    }

    public void update(UUID instanceId, ProcessStatus status, Set<String> tags, Instant startAt,
                       Map<String, Object> requirements, UUID repoId, String repoUrl, String repoPath,
                       String commitId, String commitMsg) {
        tx(tx -> update(tx, instanceId, status, tags, startAt, requirements, repoId, repoUrl, repoPath, commitId, commitMsg));
    }

    public void update(DSLContext tx, UUID instanceId, ProcessStatus status) {
        update(tx, instanceId, status, null, null, null, null, null, null, null, null);
    }

    public void update(DSLContext tx, UUID instanceId, ProcessStatus status, Set<String> tags, Instant startAt,
                       Map<String, Object> requirements, UUID repoId, String repoUrl, String repoPath,
                       String commitId, String commitMsg) {

        UpdateSetMoreStep<ProcessQueueRecord> q = tx.update(PROCESS_QUEUE)
                .set(PROCESS_QUEUE.CURRENT_STATUS, status.toString())
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

        int i = q
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                .execute();

        if (i != 1) {
            throw new DataAccessException("Invalid number of rows updated: " + i);
        }
    }

    public boolean update(UUID instanceId, ProcessStatus expected, ProcessStatus status) {
        return txResult(tx -> {
            int i = tx.update(PROCESS_QUEUE)
                    .set(PROCESS_QUEUE.CURRENT_STATUS, status.toString())
                    .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp())
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_QUEUE.CURRENT_STATUS.eq(expected.toString())))
                    .execute();

            return i == 1;
        });
    }

    public boolean update(List<UUID> instanceIds, ProcessStatus status, List<ProcessStatus> expected) {
        return txResult(tx -> {
            UpdateConditionStep q = tx.update(PROCESS_QUEUE)
                    .set(PROCESS_QUEUE.CURRENT_STATUS, status.toString())
                    .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp())
                    .where(PROCESS_QUEUE.INSTANCE_ID.in(instanceIds));

            if (expected != null) {
                List<String> l = expected.stream()
                        .map(Enum::toString)
                        .collect(Collectors.toList());

                q.and(PROCESS_QUEUE.CURRENT_STATUS.in(l));
            }

            int i = q.execute();
            return i == instanceIds.size();
        });
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

    public ProcessEntry get(UUID instanceId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.selectFrom(V_PROCESS_QUEUE)
                    .where(V_PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                    .orderBy(V_PROCESS_QUEUE.CREATED_AT.desc())
                    .fetchOne(ProcessQueueDao::toEntry);
        }
    }

    public List<ProcessEntry> get(List<UUID> instanceId) {
        try (DSLContext tx = DSL.using(cfg)) {
            List<VProcessQueueRecord> r = tx.selectFrom(V_PROCESS_QUEUE)
                    .where(V_PROCESS_QUEUE.INSTANCE_ID.in(instanceId))
                    .fetch();


            if (r == null) {
                return null;
            }

            return toEntryList(r);
        }
    }

    public List<IdAndStatus> getCascade(UUID parentInstanceId) {
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
                    .fetch(r -> new IdAndStatus(r.get(0, UUID.class), ProcessStatus.valueOf(r.get(1, String.class))));
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

    public ProcessEntry poll(Map<String, Object> capabilities) {
        return txResult(tx -> {
            SelectWhereStep<Record1<UUID>> s = tx.select(PROCESS_QUEUE.INSTANCE_ID)
                    .from(PROCESS_QUEUE);

            s.where(PROCESS_QUEUE.CURRENT_STATUS.eq(ProcessStatus.ENQUEUED.toString())
                    .and(or(PROCESS_QUEUE.START_AT.isNull(),
                            PROCESS_QUEUE.START_AT.le(currentTimestamp()))));

            if (capabilities != null && !capabilities.isEmpty()) {
                Field<Object> agentReqField = field("{0}->'agent'", Object.class, PROCESS_QUEUE.REQUIREMENTS);
                Field<Object> capabilitiesField = field("?::jsonb", Object.class, value(serialize(capabilities)));
                s.where(PROCESS_QUEUE.REQUIREMENTS.isNull()
                        .or(field("{0} <@ {1}", Boolean.class, agentReqField, capabilitiesField)));
            }

            UUID id = s.orderBy(PROCESS_QUEUE.CREATED_AT)
                    .limit(1)
                    .forUpdate()
                    .skipLocked()
                    .fetchOne(PROCESS_QUEUE.INSTANCE_ID);

            if (id == null) {
                return null;
            }

            update(tx, id, ProcessStatus.STARTING);

            return tx.selectFrom(V_PROCESS_QUEUE)
                    .where(V_PROCESS_QUEUE.INSTANCE_ID.eq(id))
                    .fetchOne(ProcessQueueDao::toEntry);
        });
    }

    public List<ProcessEntry> list(ProcessFilter filter, int limit) {
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

            if (filter.getBeforeCreatedAt() != null) {
                s.where(V_PROCESS_QUEUE.CREATED_AT.lessThan(filter.getBeforeCreatedAt()));
            }

            if (filter.getInitiator() != null) {
                s.where(V_PROCESS_QUEUE.INITIATOR.startsWith(filter.getInitiator()));
            }

            if (filter.getProcessStatus() != null) {
                s.where(V_PROCESS_QUEUE.CURRENT_STATUS.eq(filter.getProcessStatus().name()));
            }

            filterByTags(s, filter.getTags());

            return s.orderBy(V_PROCESS_QUEUE.CREATED_AT.desc())
                    .limit(limit)
                    .fetch(ProcessQueueDao::toEntry);
        }
    }

    public List<ProcessEntry> list(UUID parentInstanceId, Set<String> tags) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectConditionStep<VProcessQueueRecord> s = tx.selectFrom(V_PROCESS_QUEUE)
                    .where(V_PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(parentInstanceId));

            filterByTags(s, tags);

            return s.orderBy(V_PROCESS_QUEUE.CREATED_AT.desc())
                    .fetch(ProcessQueueDao::toEntry);
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

    public boolean exists(UUID instanceId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.fetchExists(tx.selectFrom(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId)));
        }
    }

    private String serialize(Object details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ProcessEntry toEntry(VProcessQueueRecord r) {
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
                r.getLastUpdatedAt(),
                ProcessStatus.valueOf(r.getCurrentStatus()),
                r.getLastAgentId(),
                tags,
                toSet(r.getChildrenIds()));
    }

    private static Set<UUID> toSet(UUID[] arr) {
        if (arr == null) {
            return null;
        }
        return new HashSet<>(Arrays.asList(arr));
    }

    private static List<ProcessEntry> toEntryList(List<VProcessQueueRecord> records) {
        return records.stream()
                .map(ProcessQueueDao::toEntry)
                .collect(Collectors.toList());
    }

    private static String[] toArray(Set<String> s) {
        if (s == null || s.isEmpty()) {
            return new String[0];
        }

        return s.toArray(new String[0]);
    }

    public static class IdAndStatus {

        private final UUID instanceId;
        private final ProcessStatus status;

        public IdAndStatus(UUID instanceId, ProcessStatus status) {
            this.instanceId = instanceId;
            this.status = status;
        }

        public UUID getInstanceId() {
            return instanceId;
        }

        public ProcessStatus getStatus() {
            return status;
        }
    }
}
