package com.walmartlabs.concord.server.process.queue;

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
import java.util.*;

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
        update(instanceId, status, (Set<String>) null);
    }

    public void update(UUID instanceId, ProcessStatus status, Set<String> tags) {
        tx(tx -> update(tx, instanceId, status, tags));
    }

    public void update(DSLContext tx, UUID instanceId, ProcessStatus status) {
        update(tx, instanceId, status, null);
    }

    public void update(DSLContext tx, UUID instanceId, ProcessStatus status, Set<String> tags) {
        UpdateSetMoreStep<ProcessQueueRecord> q = tx.update(PROCESS_QUEUE)
                .set(PROCESS_QUEUE.CURRENT_STATUS, status.toString())
                .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp());

        if (tags != null) {
            q.set(PROCESS_QUEUE.PROCESS_TAGS, toArray(tags));
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
            return get(tx, instanceId);
        }
    }

    public ProcessEntry get(DSLContext tx, UUID instanceId) {
        VProcessQueueRecord r = tx.selectFrom(V_PROCESS_QUEUE)
                .where(V_PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                .fetchOne();

        if (r == null) {
            return null;
        }

        return toEntry(r);
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
            VProcessQueueRecord r = tx.selectFrom(V_PROCESS_QUEUE)
                    .where(V_PROCESS_QUEUE.CURRENT_STATUS.eq(ProcessStatus.ENQUEUED.toString()))
                    .orderBy(V_PROCESS_QUEUE.CREATED_AT)
                    .limit(1)
                    .forUpdate()
                    .skipLocked()
                    .fetchOne();

            if (r == null) {
                return null;
            }

            update(tx, r.getInstanceId(), ProcessStatus.STARTING);

            return toEntry(r);
        });
    }

    public List<ProcessEntry> list() {
        return list(null, null, null, DEFAULT_LIST_LIMIT);
    }

    public List<ProcessEntry> list(UUID projectId, Timestamp beforeCreatedAt, Set<String> tags, int limit) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectWhereStep<VProcessQueueRecord> s = tx.selectFrom(V_PROCESS_QUEUE);

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
                r.getProjectId(),
                r.getProjectName(),
                r.getCreatedAt(),
                r.getInitiator(),
                r.getLastUpdatedAt(),
                ProcessStatus.valueOf(r.getCurrentStatus()),
                r.getLastAgentId(),
                tags);
    }

    private static String[] toArray(Set<String> s) {
        if (s == null || s.isEmpty()) {
            return new String[0];
        }

        return s.toArray(new String[s.size()]);
    }
}
