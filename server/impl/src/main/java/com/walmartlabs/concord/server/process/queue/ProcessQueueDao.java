package com.walmartlabs.concord.server.process.queue;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.jooq.public_.tables.records.ProcessQueueRecord;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

import static com.walmartlabs.concord.server.jooq.public_.tables.ProcessQueue.PROCESS_QUEUE;
import static org.jooq.impl.DSL.currentTimestamp;
import static org.jooq.impl.DSL.value;

@Named
public class ProcessQueueDao extends AbstractDao {

    @Inject
    protected ProcessQueueDao(Configuration cfg) {
        super(cfg);
    }

    public void insertInitial(String instanceId, String projectName, String initiator) {
        tx(tx -> tx.insertInto(PROCESS_QUEUE)
                .columns(PROCESS_QUEUE.INSTANCE_ID,
                        PROCESS_QUEUE.PROJECT_NAME,
                        PROCESS_QUEUE.CREATED_AT,
                        PROCESS_QUEUE.INITIATOR,
                        PROCESS_QUEUE.CURRENT_STATUS,
                        PROCESS_QUEUE.LAST_UPDATED_AT)
                .values(value(instanceId),
                        value(projectName),
                        currentTimestamp(),
                        value(initiator),
                        value(ProcessStatus.ENQUEUED.toString()),
                        currentTimestamp())
                .execute());
    }

    public void update(String instanceId, String agentId, ProcessStatus status) {
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

    public void update(String instanceId, ProcessStatus status) {
        tx(tx -> {
            int i = tx.update(PROCESS_QUEUE)
                    .set(PROCESS_QUEUE.CURRENT_STATUS, status.toString())
                    .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentTimestamp())
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                    .execute();

            if (i != 1) {
                throw new DataAccessException("Invalid number of rows updated: " + i);
            }
        });
    }

    public boolean update(String instanceId, ProcessStatus expected, ProcessStatus status) {
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

    public ProcessEntry get(String instanceId) {
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
        DSLContext tx = DSL.using(cfg);
        return tx.selectFrom(PROCESS_QUEUE)
                .orderBy(PROCESS_QUEUE.CREATED_AT.desc())
                .limit(30)
                .fetch(ProcessQueueDao::toEntry);
    }

    private static ProcessEntry toEntry(ProcessQueueRecord r) {
        return new ProcessEntry(r.getInstanceId(),
                r.getProjectName(),
                r.getCreatedAt(),
                r.getInitiator(),
                r.getLastUpdatedAt(),
                ProcessStatus.valueOf(r.getCurrentStatus()),
                r.getLastAgentId());
    }
}
