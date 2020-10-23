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
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.agent.AgentCommandsDao;
import com.walmartlabs.concord.server.agent.Commands;
import com.walmartlabs.concord.server.cfg.ProcessWatchdogConfiguration;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.ProcessKind;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import com.walmartlabs.concord.server.user.UserDao;
import org.jooq.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.db.PgUtils.interval;
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static org.jooq.impl.DSL.*;

@Named("process-queue-watchdog")
@Singleton
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
    private final AgentCommandsDao agentCommandsDao;
    private final ProcessLogManager logManager;
    private final WatchdogDao watchdogDao;
    private final UserDao userDao;
    private final PayloadManager payloadManager;
    private final ProcessManager processManager;
    private final ProcessQueueManager queueManager;

    @Inject
    public ProcessQueueWatchdog(ProcessWatchdogConfiguration cfg,
                                ProcessQueueDao queueDao,
                                AgentCommandsDao agentCommandsDao,
                                ProcessLogManager logManager,
                                WatchdogDao watchdogDao,
                                UserDao userDao,
                                PayloadManager payloadManager,
                                ProcessManager processManager,
                                ProcessQueueManager queueManager) {
        this.cfg = cfg;

        this.queueDao = queueDao;
        this.agentCommandsDao = agentCommandsDao;
        this.logManager = logManager;
        this.watchdogDao = watchdogDao;
        this.userDao = userDao;
        this.payloadManager = payloadManager;
        this.processManager = processManager;
        this.queueManager = queueManager;
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
        new ProcessTimedOutWorker().run();
    }

    private final class ProcessHandlersWorker implements Runnable {

        @Override
        public void run() {
            Field<OffsetDateTime> maxAge = currentOffsetDateTime().minus(interval(cfg.getMaxFailureHandlingAge()));

            for (PollEntry e : POLL_ENTRIES) {
                List<ProcessEntry> parents = watchdogDao.poll(e, maxAge, 1);

                for (ProcessEntry parent : parents) {
                    process(e, parent);
                }
            }
        }

        private void process(PollEntry entry, ProcessEntry parent) {
            String username = userDao.getUsername(parent.initiatorId);

            Map<String, Object> req = new HashMap<>();
            req.put(Constants.Request.ENTRY_POINT_KEY, entry.flow);
            req.put(Constants.Request.TAGS_KEY, null); // clear tags

            PartialProcessKey childKey = PartialProcessKey.create();
            try {
                Payload payload = payloadManager.createFork(childKey, parent.processKey, entry.handlerKind,
                        parent.initiatorId, username, parent.projectId, req, null,
                        null, parent.imports);

                processManager.startFork(payload);

                log.info("process -> created a new child process '{}' (parent '{}', entryPoint: '{}')",
                        childKey, parent.processKey, entry.flow);
            } catch (Exception e) {
                // remove the handler from the parent process to avoid infinite retries
                queueDao.removeHandler(parent.processKey, entry.flow);
                logManager.warn(parent.processKey, "Error while starting {} handler: {}", entry.flow, e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    private final class ProcessStalledWorker implements Runnable {
        @Override
        public void run() {
            String maxAge = cfg.getMaxStalledAge();

            watchdogDao.transaction(tx -> {
                Field<OffsetDateTime> cutOff = currentOffsetDateTime().minus(interval(maxAge));

                List<ProcessKey> pks = watchdogDao.pollStalled(tx, POTENTIAL_STALLED_STATUSES, cutOff, 1);
                for (ProcessKey pk : pks) {
                    queueManager.updateAgentId(tx, pk, null, ProcessStatus.FAILED);
                    logManager.warn(pk, "Process stalled, no heartbeat for more than '{}'", maxAge);
                    log.info("processStalled -> marked as failed: {}", pk);
                }
            });
        }
    }

    private final class ProcessStartFailuresWorker implements Runnable {
        @Override
        public void run() {
            String maxAge = cfg.getMaxStartFailureAge();

            watchdogDao.transaction(tx -> {
                Field<OffsetDateTime> cutOff = currentOffsetDateTime().minus(interval(maxAge));

                List<ProcessKey> pks = watchdogDao.pollStalled(tx, FAILED_TO_START_STATUSES, cutOff, 1);
                for (ProcessKey pk : pks) {
                    queueManager.updateAgentId(tx, pk, null, ProcessStatus.FAILED);
                    logManager.warn(pk, "Process failed to start for more than '{}'", maxAge);
                    log.info("processStartFailures -> marked as failed: {}", pk);
                }
            });
        }
    }

    private final class ProcessTimedOutWorker implements Runnable {
        @Override
        public void run() {
            watchdogDao.transaction(tx -> {
                List<TimedOutEntry> items = watchdogDao.pollExpired(tx, 1);
                for (TimedOutEntry i : items) {
                    queueManager.updateAgentId(tx, i.processKey, null, ProcessStatus.TIMED_OUT);

                    // TODO should AgentManager be used instead?
                    // TODO toString()? It should be typed
                    agentCommandsDao.insert(UUID.randomUUID(), i.agentId, Commands.cancel(i.processKey.toString()));

                    logManager.warn(i.processKey, "Process timed out ({}s limit)", i.timeout);
                    log.info("processTimedOut -> marked as timed out: {}", i.processKey);
                }
            });
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

    @Named
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

        public List<TimedOutEntry> pollExpired(DSLContext tx, int maxEntries) {
            ProcessQueue q = PROCESS_QUEUE.as("q");

            Field<?> maxAge = interval("1 second").mul(q.TIMEOUT);

            return tx.select(q.INSTANCE_ID, q.CREATED_AT, q.LAST_AGENT_ID, q.TIMEOUT)
                    .from(q)
                    .where(q.CURRENT_STATUS.eq(ProcessStatus.RUNNING.toString())
                            .and(q.LAST_RUN_AT.plus(maxAge).lessOrEqual(currentOffsetDateTime())))
                    .limit(maxEntries)
                    .forUpdate()
                    .skipLocked()
                    .fetch(WatchdogDao::toExpiredEntry);
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