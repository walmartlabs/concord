package com.walmartlabs.concord.server.process.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.api.process.ProcessKind;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.state.ProcessMetadataManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import org.eclipse.sisu.EagerSingleton;
import org.jooq.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.ProcessState.PROCESS_STATE;
import static org.jooq.impl.DSL.*;

@Named
@EagerSingleton
public class ProcessQueueWatchdog {

    private static final Logger log = LoggerFactory.getLogger(ProcessQueueWatchdog.class);

    private static final PollEntry POLL_ENTRIES[] = {
            new PollEntry(ProcessStatus.FAILED,
                    ProcessMetadataManager.ON_FAILURE_MARKER_PATH,
                    Constants.Flows.ON_FAILURE_FLOW,
                    ProcessKind.FAILURE_HANDLER, 3),

            new PollEntry(ProcessStatus.CANCELLED,
                    ProcessMetadataManager.ON_CANCEL_MARKER_PATH,
                    Constants.Flows.ON_CANCEL_FLOW,
                    ProcessKind.CANCEL_HANDLER, 3)
    };

    private static final long POLL_DELAY = 3000;
    private static final long ERROR_DELAY = 10000;

    private static final ProcessKind[] HANDLED_PROCESS_KINDS = {
            ProcessKind.DEFAULT
    };

    private static final ProcessStatus[] ACTIVE_PROCESS_STATUSES = {
            ProcessStatus.SUSPENDED,
            ProcessStatus.ENQUEUED,
            ProcessStatus.RUNNING,
            ProcessStatus.PREPARING,
            ProcessStatus.RESUMING
    };

    private final WatchdogDao watchdogDao;
    private final ProcessStateManager stateManager;
    private final ProcessQueueDao queueDao;
    private final ObjectMapper objectMapper;

    @Inject
    public ProcessQueueWatchdog(WatchdogDao watchdogDao, ProcessStateManager stateManager, ProcessQueueDao queueDao) {
        this.watchdogDao = watchdogDao;
        this.stateManager = stateManager;
        this.queueDao = queueDao;

        this.objectMapper = new ObjectMapper();

        init();
    }

    private void init() {
        Thread t = new Thread(new Worker(), "process-queue-watchdog");
        t.start();
        log.info("init -> watchdog started");
    }

    private final class Worker implements Runnable {

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    poll();
                } catch (Exception e) {
                    log.error("run -> error: {}", e.getMessage(), e);
                    sleep(ERROR_DELAY);
                }

                sleep(POLL_DELAY);
            }
        }

        private void poll() {
            watchdogDao.transaction(tx -> {
                Field<Timestamp> maxAge = currentTimestamp().minus(field("interval '7 days'"));

                for (PollEntry e : POLL_ENTRIES) {
                    List<ProcessEntry> parents = watchdogDao.poll(tx, e, maxAge, 1);

                    for (ProcessEntry parent : parents) {
                        UUID childId = UUID.randomUUID();
                        queueDao.insertInitial(tx, childId, e.handlerKind, parent.instanceId, parent.projectName, parent.initiator);

                        stateManager.copy(tx, parent.instanceId, childId);
                        updateRequestData(tx, childId, e.flow);

                        queueDao.update(tx, childId, ProcessStatus.ENQUEUED);
                        log.info("run -> created a new child process '{}' (parent '{}', entryPoint: '{}')",
                                childId, parent.instanceId, e.flow);
                    }
                }
            });
        }

        private void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void updateRequestData(DSLContext tx, UUID instanceId, String entryPoint) {
        String resource = Constants.Files.REQUEST_DATA_FILE_NAME;

        Optional<Map<String, Object>> o = stateManager.get(tx, instanceId, resource, in -> {
            try {
                return Optional.of(objectMapper.readValue(in, Map.class));
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        });

        Map<String, Object> m = o.orElseThrow(() -> {
            String msg = "Can't update the entry point for " + instanceId + ": request data not found";
            return new ProcessException(instanceId, msg);
        });

        m.put(Constants.Request.ENTRY_POINT_KEY, entryPoint);

        try {
            byte[] ab = objectMapper.writeValueAsBytes(m);
            stateManager.update(tx, instanceId, resource, ab);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private static final class PollEntry {

        private final ProcessStatus status;
        private final String marker;
        private final String flow;
        private final ProcessKind handlerKind;
        private final int maxTries;

        private PollEntry(ProcessStatus status, String marker, String flow, ProcessKind handlerKind, int maxTries) {
            this.status = status;
            this.marker = marker;
            this.flow = flow;
            this.handlerKind = handlerKind;
            this.maxTries = maxTries;
        }
    }

    @Named
    private static final class WatchdogDao extends AbstractDao {

        @Inject
        public WatchdogDao(Configuration cfg) {
            super(cfg);
        }

        public void transaction(Tx t) {
            tx(t);
        }

        public List<ProcessEntry> poll(DSLContext tx, PollEntry entry, Field<Timestamp> maxAge, int maxEntries) {
            ProcessQueue q = PROCESS_QUEUE.as("q");

            return tx.select(q.INSTANCE_ID, q.CURRENT_STATUS, q.PROJECT_NAME, q.INITIATOR)
                    .from(q)
                    .where(q.PROCESS_KIND.in(toArray(HANDLED_PROCESS_KINDS))
                            .and(q.CURRENT_STATUS.eq(entry.status.toString()))
                            .and(q.CREATED_AT.greaterOrEqual(maxAge))
                            .and(existsMarker(q.INSTANCE_ID, entry.marker))
                            .and(notExistsSuccess(tx, q.INSTANCE_ID, entry.handlerKind))
                            .and(count(tx, q.INSTANCE_ID, entry.handlerKind).lessThan(entry.maxTries))
                            .and(noRunningChildren(tx, q.INSTANCE_ID)))
                    .limit(maxEntries)
                    .forUpdate()
                    .fetch(WatchdogDao::toEntry);
        }

        private Field<Number> count(DSLContext tx, Field<UUID> parentInstanceId, ProcessKind kind) {
            return tx.selectCount()
                    .from(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(parentInstanceId)
                            .and(PROCESS_QUEUE.PROCESS_KIND.eq(kind.toString())))
                    .asField();
        }

        private Condition notExistsSuccess(DSLContext tx, Field<UUID> parentInstanceId, ProcessKind kind) {
            return notExists(tx.selectFrom(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(parentInstanceId)
                            .and(PROCESS_QUEUE.PROCESS_KIND.eq(kind.toString()))
                            .and(PROCESS_QUEUE.CURRENT_STATUS.eq(ProcessStatus.FINISHED.toString()))));
        }

        private Condition noRunningChildren(DSLContext tx, Field<UUID> parentInstanceId) {
            return notExists(tx.selectFrom(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(parentInstanceId)
                            .and(PROCESS_QUEUE.CURRENT_STATUS.in(toArray(ACTIVE_PROCESS_STATUSES)))));
        }

        private Condition existsMarker(Field<UUID> instanceId, String marker) {
            return exists(selectFrom(PROCESS_STATE).where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                    .and(PROCESS_STATE.ITEM_PATH.eq(marker))));
        }

        private static String[] toArray(Enum<?>... e) {
            String[] as = new String[e.length];
            for (int i = 0; i < e.length; i++) {
                as[i] = e[i].toString();
            }
            return as;
        }

        private static ProcessEntry toEntry(Record4<UUID, String, String, String> r) {
            return new ProcessEntry(r.get(PROCESS_QUEUE.INSTANCE_ID),
                    ProcessStatus.valueOf(r.get(PROCESS_QUEUE.CURRENT_STATUS)),
                    r.get(PROCESS_QUEUE.PROJECT_NAME),
                    r.get(PROCESS_QUEUE.INITIATOR));
        }
    }

    private static final class ProcessEntry implements Serializable {

        private final UUID instanceId;
        private final ProcessStatus status;
        private final String projectName;
        private final String initiator;

        private ProcessEntry(UUID instanceId, ProcessStatus status, String projectName, String initiator) {
            this.instanceId = instanceId;
            this.status = status;
            this.projectName = projectName;
            this.initiator = initiator;
        }
    }
}
