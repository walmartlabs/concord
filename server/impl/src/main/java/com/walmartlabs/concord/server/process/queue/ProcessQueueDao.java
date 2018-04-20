package com.walmartlabs.concord.server.process.queue;

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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessKind;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessQueueRecord;
import com.walmartlabs.concord.server.jooq.tables.records.VProcessQueueRecord;
import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
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

    private static final int DEFAULT_LIST_LIMIT = 30;

    @Inject
    protected ProcessQueueDao(Configuration cfg) {
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
        update(instanceId, status, (Set<String>) null, null);
    }

    public void update(UUID instanceId, ProcessStatus status, Set<String> tags, Instant startAt) {
        tx(tx -> update(tx, instanceId, status, tags, startAt));
    }

    public void update(DSLContext tx, UUID instanceId, ProcessStatus status) {
        update(tx, instanceId, status, null, null);
    }

    public void update(DSLContext tx, UUID instanceId, ProcessStatus status, Set<String> tags, Instant startAt) {
        UpdateSetMoreStep<ProcessQueueRecord> q = tx.update(PROCESS_QUEUE)
                .set(PROCESS_QUEUE.CURRENT_STATUS, status.toString())
                .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp());

        if (tags != null) {
            q.set(PROCESS_QUEUE.PROCESS_TAGS, toArray(tags));
        }

        if (startAt != null) {
            q.set(PROCESS_QUEUE.START_AT, Timestamp.from(startAt));
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

    public List<ProcessEntry> getCascade(UUID parentInstanceId) {

        try (DSLContext tx = DSL.using(cfg)) {

            List<VProcessQueueRecord> res= tx.withRecursive("children").as(
                    select()
                            .from(V_PROCESS_QUEUE)
                            .where(V_PROCESS_QUEUE.INSTANCE_ID.eq(parentInstanceId))
                            .unionAll(
                                    select()
                                            .from(V_PROCESS_QUEUE)
                                            .join(name("children"))
                                            .on(V_PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(
                                                    field(name("children", "INSTANCE_ID"), UUID.class))
                                            )))
                    .select()
                    .from(name("children"))
                    .fetchInto(VProcessQueueRecord.class);

            return toEntryList(res);
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

    public ProcessEntry poll() {
        return txResult(tx -> {
            UUID id = tx.select(PROCESS_QUEUE.INSTANCE_ID)
                    .from(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.CURRENT_STATUS.eq(ProcessStatus.ENQUEUED.toString())
                            .and(or(PROCESS_QUEUE.START_AT.isNull(),
                                    PROCESS_QUEUE.START_AT.le(currentTimestamp()))))
                    .orderBy(PROCESS_QUEUE.CREATED_AT)
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

    public List<ProcessEntry> list(Set<UUID> orgIds, boolean includeWoProjects, UUID projectId, Timestamp beforeCreatedAt, Set<String> tags, int limit) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectWhereStep<VProcessQueueRecord> s = tx.selectFrom(V_PROCESS_QUEUE);

            if (orgIds != null && !orgIds.isEmpty()) {
                SelectConditionStep<Record1<UUID>> projectIds = select(PROJECTS.PROJECT_ID)
                        .from(PROJECTS)
                        .where(PROJECTS.ORG_ID.in(orgIds));

                if (includeWoProjects) {
                    s.where(V_PROCESS_QUEUE.PROJECT_ID.in(projectIds)
                            .or(V_PROCESS_QUEUE.PROJECT_ID.isNull()));
                } else {
                    s.where(V_PROCESS_QUEUE.PROJECT_ID.in(projectIds));
                }
            }

            if (projectId != null) {
                s.where(V_PROCESS_QUEUE.PROJECT_ID.eq(projectId));
            }

            if (beforeCreatedAt != null) {
                s.where(V_PROCESS_QUEUE.CREATED_AT.lessThan(beforeCreatedAt));
            }

            filterByTags(s, tags);

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

        String[] as = tags.toArray(new String[tags.size()]);
        return q.where(PgUtils.contains(V_PROCESS_QUEUE.PROCESS_TAGS, as));
    }

    private SelectConditionStep<VProcessQueueRecord> filterByTags(SelectConditionStep<VProcessQueueRecord> q, Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return q;
        }

        String[] as = tags.toArray(new String[tags.size()]);
        return q.and(PgUtils.contains(V_PROCESS_QUEUE.PROCESS_TAGS, as));
    }

    public boolean exists(UUID instanceId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.fetchExists(tx.selectFrom(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId)));
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

        return s.toArray(new String[s.size()]);
    }
}
