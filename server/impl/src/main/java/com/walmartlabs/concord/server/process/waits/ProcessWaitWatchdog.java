package com.walmartlabs.concord.server.process.waits;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.cfg.ProcessWaitWatchdogConfiguration;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
<<<<<<< HEAD:server/impl/src/main/java/com/walmartlabs/concord/server/process/queue/ProcessWaitWatchdog.java
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.ProcessManager;
=======
import com.walmartlabs.concord.server.jooq.tables.ProcessWaitConditions;
>>>>>>> origin/master:server/impl/src/main/java/com/walmartlabs/concord/server/process/waits/ProcessWaitWatchdog.java
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import org.immutables.value.Value;
import org.jooq.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

<<<<<<< HEAD:server/impl/src/main/java/com/walmartlabs/concord/server/process/queue/ProcessWaitWatchdog.java
import static com.walmartlabs.concord.db.PgUtils.jsonbBuildArray;
import static com.walmartlabs.concord.db.PgUtils.jsonbTypeOf;
=======
import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_WAIT_CONDITIONS;
>>>>>>> origin/master:server/impl/src/main/java/com/walmartlabs/concord/server/process/waits/ProcessWaitWatchdog.java
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static org.jooq.impl.DSL.when;

/**
 * Takes care of processes with wait conditions.
 * E.g. waiting for other processes to finish, locking, etc.
 */
@Named("process-wait-watchdog")
@Singleton
public class ProcessWaitWatchdog implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ProcessWaitWatchdog.class);

    private static final Set<ProcessStatus> FINAL_STATUSES = new HashSet<>(Arrays.asList(
            ProcessStatus.FINISHED,
            ProcessStatus.FAILED,
            ProcessStatus.CANCELLED,
            ProcessStatus.TIMED_OUT));

    private final ProcessWaitWatchdogConfiguration cfg;
    private final WatchdogDao dao;
<<<<<<< HEAD:server/impl/src/main/java/com/walmartlabs/concord/server/process/queue/ProcessWaitWatchdog.java
    private final ProcessQueueManager queueManager;
    private final ProcessManager processManager;
    private final PayloadManager payloadManager;
=======
    private final WatchdogDaoOld daoOld;
    private final ProcessWaitManager processWaitManager;
>>>>>>> origin/master:server/impl/src/main/java/com/walmartlabs/concord/server/process/waits/ProcessWaitWatchdog.java
    private final Map<WaitType, ProcessWaitHandler<AbstractWaitCondition>> processWaitHandlers;

    @Inject
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ProcessWaitWatchdog(ProcessWaitWatchdogConfiguration cfg,
                               WatchdogDao dao,
<<<<<<< HEAD:server/impl/src/main/java/com/walmartlabs/concord/server/process/queue/ProcessWaitWatchdog.java
                               ProcessQueueManager queueManager,
                               Set<ProcessWaitHandler> handlers,
                               ProcessManager processManager,
                               PayloadManager payloadManager) {

        this.cfg = cfg;
        this.dao = dao;
        this.queueManager = queueManager;
        this.processManager = processManager;
        this.payloadManager = payloadManager;
=======
                               WatchdogDaoOld daoOld,
                               ProcessWaitManager processWaitManager,
                               Set<ProcessWaitHandler> handlers) {

        this.cfg = cfg;
        this.dao = dao;
        this.daoOld = daoOld;
        this.processWaitManager = processWaitManager;
>>>>>>> origin/master:server/impl/src/main/java/com/walmartlabs/concord/server/process/waits/ProcessWaitWatchdog.java
        this.processWaitHandlers = new HashMap<>();

        handlers.forEach(h -> this.processWaitHandlers.put(h.getType(), h));
    }

    @Override
    public long getIntervalInSec() {
        return cfg.getPeriod().getSeconds();
    }

    @Override
    public void performTask() {
        process(dao);
        process(daoOld);
    }

    private void process(PollDao dao) {
        Long lastId = null;
        while (true) {
            List<WaitingProcess> processes = dao.nextWaitItems(lastId, cfg.getPollLimit());
            if (processes.isEmpty()) {
                return;
            }

            for (WaitingProcess p : processes) {
                process(p);
                lastId = p.id();
            }
        }
    }

    private void process(WaitingProcess p) {
        Set<String> resumeEvents = new HashSet<>();
        List<AbstractWaitCondition> resultWaits = new ArrayList<>();
        for (AbstractWaitCondition w : p.waits()) {
            ProcessWaitHandler.Result<AbstractWaitCondition> result = processWait(w, p);
            if (result != null) {
                if (result.waitCondition() != null){
                    resultWaits.add(result.waitCondition());
                } else if (result.resumeEvent() != null) {
                    resumeEvents.add(result.resumeEvent());
                }
            }
        }

        if (p.waits().equals(resultWaits)) {
            return;
        }

        try {
            if (!resumeEvents.isEmpty()) {
                resumeProcess(p.processKey(), resumeEvents);
            }
            queueManager.setWait(p.processKey(), resultWaits);
        } catch (Exception e) {
            log.info("process ['{}'] -> error", p, e);
        }
    }

    private ProcessWaitHandler.Result<AbstractWaitCondition> processWait(AbstractWaitCondition w, WaitingProcess p) {
        WaitType type = w.type();

        ProcessWaitHandler<AbstractWaitCondition> handler = processWaitHandlers.get(type);
        if (handler == null) {
            log.warn("processHandler ['{}'] -> handler '{}' not found", p.processKey(), type);
<<<<<<< HEAD:server/impl/src/main/java/com/walmartlabs/concord/server/process/queue/ProcessWaitWatchdog.java
            return null;
=======
            return;
>>>>>>> origin/master:server/impl/src/main/java/com/walmartlabs/concord/server/process/waits/ProcessWaitWatchdog.java
        }

        // TODO: remove me in the next release
        if (p.status() != null && !handler.getProcessStatuses().contains(p.status())) {
            // clear wait conditions for finished processes
            if (FINAL_STATUSES.contains(p.status())) {
<<<<<<< HEAD:server/impl/src/main/java/com/walmartlabs/concord/server/process/queue/ProcessWaitWatchdog.java
                return null;
=======
                processWaitManager.updateWaitOld(p.processKey(), null);
>>>>>>> origin/master:server/impl/src/main/java/com/walmartlabs/concord/server/process/waits/ProcessWaitWatchdog.java
            }
            return ProcessWaitHandler.Result.of(w);
        }

        try {
<<<<<<< HEAD:server/impl/src/main/java/com/walmartlabs/concord/server/process/queue/ProcessWaitWatchdog.java
            return handler.process(p.processKey(), p.status(), w);
=======
            AbstractWaitCondition originalWaits = p.waits();
            AbstractWaitCondition processedWaits = handler.process(p.processKey(), p.status(), originalWaits);
            if (!originalWaits.equals(processedWaits)) {
                processWaitManager.updateWait(p.processKey(), processedWaits);

                // TODO: remove me in the next release
                if (p.status() != null) {
                    processWaitManager.updateWaitOld(p.processKey(), processedWaits);
                }
            }
>>>>>>> origin/master:server/impl/src/main/java/com/walmartlabs/concord/server/process/waits/ProcessWaitWatchdog.java
        } catch (Exception e) {
            log.info("processWait ['{}', '{}'] -> error", type, p, e);
            return ProcessWaitHandler.Result.of(w);
        }
    }

    private void resumeProcess(ProcessKey key, Set<String> events) {
        Payload payload;
        try {
            payload = payloadManager.createResumePayload(key, events, null);
        } catch (IOException e) {
            throw new RuntimeException("Error creating a payload", e);
        }

        processManager.resume(payload);
        log.info("resumeProcess ['{}', '{}'] -> done", key, events);
    }

    @Value.Immutable
    interface WaitingProcess {

        ProcessKey processKey();

        // TODO: remove me in the next release
        @Nullable
        ProcessStatus status();

        long id();

        List<AbstractWaitCondition> waits();

        static ImmutableWaitingProcess.Builder builder() {
            return ImmutableWaitingProcess.builder();
        }
    }

    private interface PollDao {

        List<WaitingProcess> nextWaitItems(Long lastId, int pollLimit);
    }

    @Named
    private static final class WatchdogDao extends AbstractDao implements PollDao {

        private static final TypeReference<List<AbstractWaitCondition>> WAIT_LIST = new TypeReference<List<AbstractWaitCondition>>() {
        };

        private final ConcordObjectMapper objectMapper;

        @Inject
        public WatchdogDao(@MainDB Configuration cfg, ConcordObjectMapper objectMapper) {
            super(cfg);

            this.objectMapper = objectMapper;
        }

        @Override
        public List<WaitingProcess> nextWaitItems(Long lastId, int pollLimit) {
            return txResult(tx -> {
                ProcessWaitConditions w = PROCESS_WAIT_CONDITIONS.as("w");
                SelectConditionStep<Record4<UUID, OffsetDateTime, Long, JSONB>> s = tx.select(
                        w.INSTANCE_ID,
                        w.INSTANCE_CREATED_AT,
                        w.ID_SEQ,
                        w.WAIT_CONDITIONS)
                        .from(w)
                        .where(w.IS_WAITING.eq(true));

                if (lastId != null) {
                    s.and(w.ID_SEQ.greaterThan(lastId));
                }

                return s.orderBy(w.ID_SEQ)
                        .limit(pollLimit)
                        .fetch(r -> WaitingProcess.builder()
                                .processKey(new ProcessKey(r.value1(), r.value2()))
                                .id(r.value3())
                                .waits(objectMapper.fromJSONB(r.value4(), AbstractWaitCondition.class))
                                .build());
            });
        }
    }

    @Named
    private static final class WatchdogDaoOld extends AbstractDao implements PollDao {

        private final ConcordObjectMapper objectMapper;

        @Inject
        public WatchdogDaoOld(@MainDB Configuration cfg, ConcordObjectMapper objectMapper) {
            super(cfg);

            this.objectMapper = objectMapper;
        }

        @Override
        public List<WaitingProcess> nextWaitItems(Long lastId, int pollLimit) {
            return txResult(tx -> {
                ProcessQueue q = PROCESS_QUEUE.as("q");

                // TODO: remove me in next version (after all wait conditions is arrays)
                Field<JSONB> waitConditionsAsArray = when(jsonbTypeOf(q.WAIT_CONDITIONS).eq("object"), jsonbBuildArray(q.WAIT_CONDITIONS)).else_(q.WAIT_CONDITIONS);

                SelectConditionStep<Record5<UUID, String, OffsetDateTime, Long, JSONB>> s = tx.select(
                        q.INSTANCE_ID,
                        q.CURRENT_STATUS,
                        q.CREATED_AT,
                        q.ID_SEQ,
                        waitConditionsAsArray)
                        .from(q)
                        .where(q.WAIT_CONDITIONS.isNotNull());

                if (lastId != null) {
                    s.and(q.ID_SEQ.greaterThan(lastId));
                }

                return s.orderBy(q.ID_SEQ)
                        .limit(pollLimit)
                        .fetch(r -> WaitingProcess.builder()
                                .processKey(new ProcessKey(r.value1(), r.value3()))
                                .status(ProcessStatus.valueOf(r.value2()))
                                .id(r.value4())
                                .waits(objectMapper.fromJSONB(r.value5(), WAIT_LIST))
                                .build());
            });
        }
    }
}
