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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.cfg.ProcessWaitWatchdogConfiguration;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.jooq.tables.ProcessWaits;
import com.walmartlabs.concord.server.process.queue.*;
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
import java.time.OffsetDateTime;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_WAITS;
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;

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
    private final WatchdogDaoOld daoOld;
    private final ProcessWaitManager processWaitManager;
    private final Map<WaitType, ProcessWaitHandler<AbstractWaitCondition>> processWaitHandlers;

    @Inject
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ProcessWaitWatchdog(ProcessWaitWatchdogConfiguration cfg,
                               WatchdogDao dao,
                               WatchdogDaoOld daoOld,
                               ProcessWaitManager processWaitManager,
                               Set<ProcessWaitHandler> handlers) {

        this.cfg = cfg;
        this.dao = dao;
        this.daoOld = daoOld;
        this.processWaitManager = processWaitManager;
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
                WaitType type = p.waits().type();
                processHandler(type, p);
                lastId = p.id();
            }
        }
    }

    private void processHandler(WaitType type, WaitingProcess p) {
        ProcessWaitHandler<AbstractWaitCondition> handler = processWaitHandlers.get(type);
        if (handler == null) {
            log.warn("processHandler ['{}'] -> handler '{}' not found", p.instanceId(), type);
            return;
        }

        // TODO: remove me in the next release
        if (p.status() != null && !handler.getProcessStatuses().contains(p.status())) {
            // clear wait conditions for finished processes
            if (FINAL_STATUSES.contains(p.status())) {
                processWaitManager.updateWaitOld(new ProcessKey(p.instanceId(), p.instanceCreatedAt()), null);
            }
            return;
        }

        try {
            AbstractWaitCondition originalWaits = p.waits();
            AbstractWaitCondition processedWaits = handler.process(p.instanceId(), p.status(), originalWaits);
            if (!originalWaits.equals(processedWaits)) {
                processWaitManager.updateWait(new ProcessKey(p.instanceId(), p.instanceCreatedAt()), processedWaits);

                // TODO: remove me in the next release
                if (p.status() != null) {
                    processWaitManager.updateWaitOld(new ProcessKey(p.instanceId(), p.instanceCreatedAt()), processedWaits);
                }
            }
        } catch (Exception e) {
            log.info("processHandler ['{}', '{}'] -> error", type, p, e);
        }
    }

    @Value.Immutable
    interface WaitingProcess {

        UUID instanceId();

        // TODO: remove me in the next release
        @Nullable
        ProcessStatus status();

        OffsetDateTime instanceCreatedAt();

        long id();

        AbstractWaitCondition waits();

        static ImmutableWaitingProcess.Builder builder() {
            return ImmutableWaitingProcess.builder();
        }
    }

    private interface PollDao {

        List<WaitingProcess> nextWaitItems(Long lastId, int pollLimit);
    }

    @Named
    private static final class WatchdogDao extends AbstractDao implements PollDao {

        private final ConcordObjectMapper objectMapper;

        @Inject
        public WatchdogDao(@MainDB Configuration cfg, ConcordObjectMapper objectMapper) {
            super(cfg);

            this.objectMapper = objectMapper;
        }

        @Override
        public List<WaitingProcess> nextWaitItems(Long lastId, int pollLimit) {
            return txResult(tx -> {
                ProcessWaits w = PROCESS_WAITS.as("w");
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
                                .instanceId(r.value1())
                                .instanceCreatedAt(r.value2())
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
                SelectConditionStep<Record5<UUID, String, OffsetDateTime, Long, JSONB>> s = tx.select(
                        q.INSTANCE_ID,
                        q.CURRENT_STATUS,
                        q.CREATED_AT,
                        q.ID_SEQ,
                        q.WAIT_CONDITIONS)
                        .from(q)
                        .where(q.WAIT_CONDITIONS.isNotNull());

                if (lastId != null) {
                    s.and(q.ID_SEQ.greaterThan(lastId));
                }

                return s.orderBy(q.ID_SEQ)
                        .limit(pollLimit)
                        .fetch(r -> WaitingProcess.builder()
                                .instanceId(r.value1())
                                .status(ProcessStatus.valueOf(r.value2()))
                                .instanceCreatedAt(r.value3())
                                .id(r.value4())
                                .waits(objectMapper.fromJSONB(r.value5(), AbstractWaitCondition.class))
                                .build());
            });
        }
    }
}
