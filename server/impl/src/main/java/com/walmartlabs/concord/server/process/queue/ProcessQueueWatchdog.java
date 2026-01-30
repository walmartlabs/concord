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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.LogTags;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.agent.AgentManager;
import com.walmartlabs.concord.server.cfg.ProcessWatchdogConfiguration;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import com.walmartlabs.concord.server.security.UserSecurityContext;
import com.walmartlabs.concord.server.user.UserDao;
import org.jooq.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.db.PgUtils.interval;
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static org.jooq.impl.DSL.*;

public class ProcessQueueWatchdog implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ProcessQueueWatchdog.class);

    private static final PollEntry[] POLL_ENTRIES = {
            new PollEntry(ProcessStatus.FAILED,
                    Constants.Flows.ON_FAILURE_FLOW,
                    ProcessKind.FAILURE_HANDLER, 3),

            new PollEntry(ProcessStatus.CANCELLED,
                    Constants.Flows.ON_CANCEL_FLOW,
                    ProcessKind.CANCEL_HANDLER, 3),

            new PollEntry(ProcessStatus.TIMED_OUT,
                    Constants.Flows.ON_TIMEOUT_FLOW,
                    ProcessKind.TIMEOUT_HANDLER, 3)
    };

    private static final ProcessKind[] HANDLED_PROCESS_KINDS = {
            ProcessKind.DEFAULT
    };

    private static final ProcessKind[] SPECIAL_HANDLERS = {
            ProcessKind.FAILURE_HANDLER,
            ProcessKind.CANCEL_HANDLER,
            ProcessKind.TIMEOUT_HANDLER
    };

    private static final ProcessStatus[] ACTIVE_PROCESS_STATUSES = {
            ProcessStatus.NEW,
            ProcessStatus.PREPARING,
            ProcessStatus.ENQUEUED,
            ProcessStatus.WAITING,
            ProcessStatus.STARTING,
            ProcessStatus.RUNNING,
            ProcessStatus.SUSPENDED,
            ProcessStatus.RESUMING
    };

    private static final ProcessStatus[] POTENTIAL_STALLED_STATUSES = {
            ProcessStatus.RUNNING
    };

    private static final ProcessStatus[] FAILED_TO_START_STATUSES = {
            ProcessStatus.PREPARING,
            ProcessStatus.STARTING,
            ProcessStatus.RESUMING
    };

    private final ProcessWatchdogConfiguration cfg;
    private final ProcessQueueDao queueDao;
    private final AgentManager agentManager;
    private final ProcessLogManager logManager;
    private final WatchdogDao watchdogDao;
    private final UserDao userDao;
    private final PayloadManager payloadManager;
    private final ProcessManager processManager;
    private final ProcessQueueManager queueManager;
    private final UserSecurityContext userSecurityContext;

    @Inject
    public ProcessQueueWatchdog(ProcessWatchdogConfiguration cfg,
                                ProcessQueueDao queueDao,
                                AgentManager agentManager,
                                ProcessLogManager logManager,
                                WatchdogDao watchdogDao,
                                UserDao userDao,
                                PayloadManager payloadManager,
                                ProcessManager processManager,
                                ProcessQueueManager queueManager,
                                UserSecurityContext userSecurityContext) {
        this.cfg = cfg;

        this.queueDao = queueDao;
        this.agentManager = agentManager;
        this.logManager = logManager;
        this.watchdogDao = watchdogDao;
        this.userDao = userDao;
        this.payloadManager = payloadManager;
        this.processManager = processManager;
        this.queueManager = queueManager;
        this.userSecurityContext = userSecurityContext;
    }

    @Override
    public String getId() {
        return "process-queue-watchdog";
    }

    @Override
    public long getIntervalInSec() {
        return cfg.getPeriod().getSeconds();
    }

    @Override
    public void performTask() {
        new ProcessHandlersWorker().run();
        new ProcessStalledWorker().run();
        new ProcessStartFailuresWorker().run();
        new ProcessTimedOutWorker(ProcessStatus.RUNNING, () -> watchdogDao.pollExpired(1)).run();
        new ProcessTimedOutWorker(ProcessStatus.SUSPENDED, () -> watchdogDao.pollSuspendExpired(1)).run();
    }

    private final class ProcessHandlersWorker implements Runnable {

        @Override
        public void run() {
            Field<OffsetDateTime> maxAge = PgUtils.nowMinus(cfg.getMaxFailureHandlingAge());
            boolean errorEncountered = false;

            for (PollEntry e : POLL_ENTRIES) {
                List<ProcessEntry> parents = watchdogDao.poll(e, maxAge, 1);

                for (ProcessEntry parent : parents) {
                    errorEncountered = process(e, parent);
                    if (errorEncountered) {
                        break;
                    }
                }

                if (errorEncountered) {
                    break;
                }
            }
        }

        /**
         * @return {@code true} on error
         */
        private boolean process(PollEntry entry, ProcessEntry parent) {
            Map<String, Object> req = new HashMap<>();
            req.put(Constants.Request.ENTRY_POINT_KEY, entry.flow);
            req.put(Constants.Request.TAGS_KEY, null); // clear tags

            PartialProcessKey childKey = PartialProcessKey.create();
            try {
                String username = assertUserEnabled(parent.initiatorId);
                Payload payload = payloadManager.createFork(childKey, parent.processKey, entry.handlerKind,
                        parent.initiatorId, username, parent.projectId, req, null,
                        null, parent.imports);

                userSecurityContext.runAs(parent.initiatorId, () -> processManager.startFork(payload));

                logManager.info(parent.processKey, "{} started: {}", toString(entry.handlerKind), LogTags.instanceId(payload.getProcessKey().getInstanceId()));

                log.info("process -> created a new child process '{}' (parent '{}', entryPoint: '{}')",
                        childKey, parent.processKey, entry.flow);
                return false;
            } catch (Exception e) {
                // remove the handler from the parent process to avoid infinite retries
                queueDao.removeHandler(parent.processKey, entry.flow);
                logManager.warn(parent.processKey, "Error while starting {} handler: {}", entry.flow, e.getMessage());
                log.warn("Error while starting {}/{} handler: {}", parent.processKey, entry.flow, e.getMessage());
                return true;
            }
        }

        /**
         * @return username
         */
        private String assertUserEnabled(UUID initiatorId) {
            var user = userDao.get(initiatorId);

            if (user == null) {
                throw new IllegalStateException("initiator not found");
            }

            if (user.isDisabled()) {
                throw new IllegalArgumentException("initiator is disabled");
            }

            return user.getName();
        }

        private String toString(ProcessKind kind) {
            switch (kind) {
                case CANCEL_HANDLER:
                    return "Cancel handler";
                case FAILURE_HANDLER:
                    return "Failure handler";
                case TIMEOUT_HANDLER:
                    return "Timeout handler";
                default:
                    throw new RuntimeException("Unknown process kind: " + kind);
            }
        }
    }

    private final class ProcessStalledWorker implements Runnable {
        @Override
        public void run() {
            watchdogDao.transaction(tx -> {
                Field<OffsetDateTime> cutoff = PgUtils.nowMinus(cfg.getMaxStalledAge());

                List<ProcessKey> pks = watchdogDao.pollStalled(tx, POTENTIAL_STALLED_STATUSES, cutoff, 1);
                for (ProcessKey pk : pks) {
                    queueManager.updateAgentId(tx, pk, null, ProcessStatus.FAILED);
                    logManager.warn(pk, "Process stalled, no heartbeat for more than '{}'", cfg.getMaxStalledAge());
                    log.info("processStalled -> marked as failed: {}", pk);
                }
            });
        }
    }

    private final class ProcessStartFailuresWorker implements Runnable {
        @Override
        public void run() {
            watchdogDao.transaction(tx -> {
                Field<OffsetDateTime> cutOff = PgUtils.nowMinus(cfg.getMaxStartFailureAge());

                List<ProcessKey> pks = watchdogDao.pollStalled(tx, FAILED_TO_START_STATUSES, cutOff, 1);
                for (ProcessKey pk : pks) {
                    queueManager.updateAgentId(tx, pk, null, ProcessStatus.FAILED);
                    logManager.warn(pk, "Process failed to start for more than '{}'", cfg.getMaxStartFailureAge());
                    log.info("processStartFailures -> marked as failed: {}", pk);
                }
            });
        }
    }

    interface TimedOutProcessPoller {

        List<TimedOutEntry> poll();
    }

    class ProcessTimedOutWorker implements Runnable {

        private final ProcessStatus expectedProcessStatus;
        private final TimedOutProcessPoller poller;

        public ProcessTimedOutWorker(ProcessStatus expectedProcessStatus, TimedOutProcessPoller poller) {
            this.expectedProcessStatus = expectedProcessStatus;
            this.poller = poller;
        }

        @Override
        public void run() {
            List<TimedOutEntry> items = poller.poll();
            for (TimedOutEntry i : items) {
                boolean updated = queueManager.updateExpectedStatus(i.processKey, expectedProcessStatus, ProcessStatus.TIMED_OUT);
                if (updated) {
                    agentManager.killProcess(i.processKey, i.agentId);
                    logManager.warn(i.processKey, "Process timed out ({}s limit)", i.timeout);
                    log.info("processTimedOut -> marked as timed out: {}", i.processKey);
                }
            }
        }
    }

    private static final class PollEntry {

        private final ProcessStatus status;
        private final String flow;
        private final ProcessKind handlerKind;
        private final int maxTries;

        private PollEntry(ProcessStatus status, String flow, ProcessKind handlerKind, int maxTries) {
            this.status = status;
            this.flow = flow;
            this.handlerKind = handlerKind;
            this.maxTries = maxTries;
        }
    }

    private static final class WatchdogDao extends AbstractDao {

        private final ConcordObjectMapper objectMapper;

        @Inject
        public WatchdogDao(@MainDB Configuration cfg, ConcordObjectMapper objectMapper) {
            super(cfg);
            this.objectMapper = objectMapper;
        }

        private void transaction(Tx t) {
            tx(t);
        }

        public List<ProcessEntry> poll(PollEntry entry, Field<OffsetDateTime> maxAge, int maxEntries) {
            ProcessQueue q = PROCESS_QUEUE.as("q");

            return txResult(tx -> tx.select(q.INSTANCE_ID, q.CREATED_AT, q.PROJECT_ID, q.INITIATOR_ID, q.IMPORTS)
                    .from(q)
                    .where(q.PROCESS_KIND.in(Utils.toString(HANDLED_PROCESS_KINDS))
                            .and(q.CURRENT_STATUS.eq(entry.status.toString()))
                            .and(q.CREATED_AT.greaterOrEqual(maxAge))
                            .and(PgUtils.contains(q.HANDLERS, new String[]{entry.flow}))
                            .and(noSuccessfulHandlers(q.INSTANCE_ID, entry.handlerKind))
                            .and(count(tx, q.INSTANCE_ID, entry.handlerKind).lessThan(entry.maxTries))
                            .and(noRunningHandlers(q.INSTANCE_ID)))
                    .limit(maxEntries)
                    .fetch(this::toEntry));
        }

        public List<ProcessKey> pollStalled(DSLContext tx, ProcessStatus[] statuses, Field<OffsetDateTime> cutOff, int maxEntries) {
            ProcessQueue q = PROCESS_QUEUE.as("q");
            return tx.select(q.INSTANCE_ID, q.CREATED_AT)
                    .from(q)
                    .where(q.CURRENT_STATUS.in(Utils.toString(statuses))
                            .and(q.LAST_UPDATED_AT.lessThan(cutOff)))
                    .limit(maxEntries)
                    .forUpdate()
                    .skipLocked()
                    .fetch(r -> new ProcessKey(r.value1(), r.value2()));
        }

        public List<TimedOutEntry> pollExpired(int maxEntries) {
            ProcessQueue q = PROCESS_QUEUE.as("q");

            Field<?> maxAge = interval("1 second").mul(q.TIMEOUT);

            return txResult(tx -> tx.select(q.INSTANCE_ID, q.CREATED_AT, q.LAST_AGENT_ID, q.TIMEOUT)
                    .from(q)
                    .where(q.CURRENT_STATUS.eq(ProcessStatus.RUNNING.toString())
                            .and(q.LAST_RUN_AT.plus(maxAge).lessOrEqual(currentOffsetDateTime())))
                    .limit(maxEntries)
                    .forUpdate()
                    .skipLocked()
                    .fetch(WatchdogDao::toExpiredEntry));
        }

        public List<TimedOutEntry> pollSuspendExpired(int maxEntries) {
            ProcessQueue q = PROCESS_QUEUE.as("q");

            Field<?> maxAge = interval("1 second").mul(q.SUSPEND_TIMEOUT);

            return txResult(tx -> tx.select(q.INSTANCE_ID, q.CREATED_AT, q.LAST_AGENT_ID, q.SUSPEND_TIMEOUT)
                    .from(q)
                    .where(q.CURRENT_STATUS.eq(ProcessStatus.SUSPENDED.toString())
                            .and(q.LAST_UPDATED_AT.plus(maxAge).lessOrEqual(currentOffsetDateTime())))
                    .limit(maxEntries)
                    .forUpdate()
                    .skipLocked()
                    .fetch(WatchdogDao::toExpiredEntry));
        }

        private Field<Number> count(DSLContext tx, Field<UUID> parentInstanceId, ProcessKind kind) {
            return tx.selectCount()
                    .from(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(parentInstanceId)
                            .and(PROCESS_QUEUE.PROCESS_KIND.eq(kind.toString())))
                    .asField();
        }

        private Condition noSuccessfulHandlers(Field<UUID> parentInstanceId, ProcessKind kind) {
            return notExists(selectOne().from(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(parentInstanceId)
                            .and(PROCESS_QUEUE.PROCESS_KIND.eq(kind.toString()))
                            .and(PROCESS_QUEUE.CURRENT_STATUS.eq(ProcessStatus.FINISHED.toString()))));
        }

        private Condition noRunningHandlers(Field<UUID> parentInstanceId) {
            return notExists(selectOne().from(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(parentInstanceId)
                            .and(PROCESS_QUEUE.CURRENT_STATUS.in(Utils.toString(ACTIVE_PROCESS_STATUSES)))
                            .and(PROCESS_QUEUE.PROCESS_KIND.in(Utils.toString(SPECIAL_HANDLERS)))));
        }

        private ProcessEntry toEntry(Record5<UUID, OffsetDateTime, UUID, UUID, JSONB> r) {
            ProcessKey processKey = new ProcessKey(r.get(PROCESS_QUEUE.INSTANCE_ID), r.get(PROCESS_QUEUE.CREATED_AT));
            return new ProcessEntry(processKey,
                    r.get(PROCESS_QUEUE.PROJECT_ID),
                    r.get(PROCESS_QUEUE.INITIATOR_ID),
                    objectMapper.fromJSONB(r.get(PROCESS_QUEUE.IMPORTS), Imports.class));
        }

        private static TimedOutEntry toExpiredEntry(Record4<UUID, OffsetDateTime, String, Long> r) {
            ProcessKey processKey = new ProcessKey(r.value1(), r.value2());
            return new TimedOutEntry(processKey, r.value3(), r.value4());
        }
    }

    private static final class ProcessEntry {

        private final ProcessKey processKey;
        private final UUID projectId;
        private final UUID initiatorId;
        private final Imports imports;

        private ProcessEntry(ProcessKey processKey, UUID projectId, UUID initiatorId, Imports imports) {
            this.processKey = processKey;
            this.projectId = projectId;
            this.initiatorId = initiatorId;
            this.imports = imports;
        }
    }

    private static class TimedOutEntry {

        private final ProcessKey processKey;
        private final String agentId;
        private final Long timeout;

        private TimedOutEntry(ProcessKey processKey, String agentId, Long timeout) {
            this.processKey = processKey;
            this.agentId = agentId;
            this.timeout = timeout;
        }
    }
}