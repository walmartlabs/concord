package com.walmartlabs.concord.server.process.queue;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessKind;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessQueueRecord;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.SelectWhereStep;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static org.jooq.impl.DSL.currentTimestamp;
import static org.jooq.impl.DSL.value;

@Named
public class ProcessQueueDao extends AbstractDao {

    private static final int DEFAULT_LIST_LIMIT = 30;

    @Inject
    protected ProcessQueueDao(Configuration cfg) {
        super(cfg);
    }

    public void insertInitial(UUID instanceId, ProcessKind kind, UUID parentInstanceId, String projectName, String initiator) {
        tx(tx -> insertInitial(tx, instanceId, kind, parentInstanceId, projectName, initiator));
    }

    public void insertInitial(DSLContext tx, UUID instanceId, ProcessKind kind, UUID parentInstanceId, String projectName, String initiator) {
        tx.insertInto(PROCESS_QUEUE)
                .columns(PROCESS_QUEUE.INSTANCE_ID,
                        PROCESS_QUEUE.PROCESS_KIND,
                        PROCESS_QUEUE.PARENT_INSTANCE_ID,
                        PROCESS_QUEUE.PROJECT_NAME,
                        PROCESS_QUEUE.CREATED_AT,
                        PROCESS_QUEUE.INITIATOR,
                        PROCESS_QUEUE.CURRENT_STATUS,
                        PROCESS_QUEUE.LAST_UPDATED_AT)
                .values(value(instanceId),
                        value(kind.toString()),
                        value(parentInstanceId),
                        value(projectName),
                        currentTimestamp(),
                        value(initiator),
                        value(ProcessStatus.PREPARING.toString()),
                        currentTimestamp())
                .execute();
    }

    public void update(UUID instanceId, String agentId, ProcessStatus status) {
        tx(tx -> {
            int i = tx.update(PROCESS_QUEUE)
                    .set(PROCESS_QUEUE.CURRENT_STATUS, status.toString())
                    .set(PROCESS_QUEUE.LAST_AGENT_ID, agentId)
                    .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp())
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                    .execute();

            if (i != 1) {
                throw new DataAccessException("Invalid number of rows updated: " + i);
            }
        });
    }

    public void update(UUID instanceId, ProcessStatus status) {
        tx(tx -> update(tx, instanceId, status));
    }

    public void update(DSLContext tx, UUID instanceId, ProcessStatus status) {
        int i = tx.update(PROCESS_QUEUE)
                .set(PROCESS_QUEUE.CURRENT_STATUS, status.toString())
                .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp())
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

    public ProcessEntry get(UUID instanceId) {
        DSLContext tx = DSL.using(cfg);

        ProcessQueueRecord r = tx.selectFrom(PROCESS_QUEUE)
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                .fetchOne();

        if (r == null) {
            return null;
        }

        return toEntry(r);
    }

    public ProcessEntry poll() {
        return txResult(tx -> {
            ProcessQueueRecord r = tx.selectFrom(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.CURRENT_STATUS.eq(ProcessStatus.ENQUEUED.toString()))
                    .orderBy(PROCESS_QUEUE.CREATED_AT)
                    .limit(1)
                    .forUpdate()
                    .fetchOne();

            if (r == null) {
                return null;
            }

            r.setCurrentStatus(ProcessStatus.STARTING.toString());
            tx.executeUpdate(r);

            return toEntry(r);
        });
    }

    public List<ProcessEntry> list() {
        return list(null, DEFAULT_LIST_LIMIT);
    }

    public List<ProcessEntry> list(Timestamp beforeCreatedAt, int limit) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectWhereStep<ProcessQueueRecord> q = tx.selectFrom(PROCESS_QUEUE);

            if (beforeCreatedAt != null) {
                q.where(PROCESS_QUEUE.CREATED_AT.lessThan(beforeCreatedAt));
            }

            return q.orderBy(PROCESS_QUEUE.CREATED_AT.desc())
                    .limit(limit)
                    .fetch(ProcessQueueDao::toEntry);
        }
    }

    public List<ProcessEntry> list(UUID parentInstanceId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.selectFrom(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(parentInstanceId))
                    .orderBy(PROCESS_QUEUE.CREATED_AT.desc())
                    .fetch(ProcessQueueDao::toEntry);
        }
    }

    private static ProcessEntry toEntry(ProcessQueueRecord r) {
        ProcessKind kind;
        String s = r.getProcessKind();
        if (s != null) {
            kind = ProcessKind.valueOf(s);
        } else {
            kind = ProcessKind.DEFAULT;
        }

        return new ProcessEntry(r.getInstanceId(),
                kind,
                r.getParentInstanceId(),
                r.getProjectName(),
                r.getCreatedAt(),
                r.getInitiator(),
                r.getLastUpdatedAt(),
                ProcessStatus.valueOf(r.getCurrentStatus()),
                r.getLastAgentId());
    }
}
