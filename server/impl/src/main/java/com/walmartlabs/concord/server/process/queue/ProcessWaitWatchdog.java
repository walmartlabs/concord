package com.walmartlabs.concord.server.process.queue;

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
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import org.immutables.value.Value;
import org.jooq.Configuration;
import org.jooq.JSONB;
import org.jooq.Record5;
import org.jooq.SelectConditionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Timestamp;
import java.util.*;

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
    private final ProcessQueueManager queueManager;
    private final Map<WaitType, ProcessWaitHandler<AbstractWaitCondition>> processWaitHandlers;

    @Inject
    @SuppressWarnings("unchecked")
    public ProcessWaitWatchdog(ProcessWaitWatchdogConfiguration cfg,
                               WatchdogDao dao,
                               ProcessQueueManager queueManager,
                               Set<ProcessWaitHandler> handlers) {

        this.cfg = cfg;
        this.dao = dao;
        this.queueManager = queueManager;
        this.processWaitHandlers = new HashMap<>();

        handlers.forEach(h -> this.processWaitHandlers.put(h.getType(), h));
    }

    @Override
    public long getIntervalInSec() {
        return cfg.getPeriod();
    }

    @Override
    public void performTask() {
        Timestamp lastUpdatedAt = null;
        while (true) {
            List<WaitingProcess> processes = dao.nextWaitItems(lastUpdatedAt, cfg.getPollLimit());
            if (processes.isEmpty()) {
                return;
            }

            for (WaitingProcess p : processes) {
                WaitType type = p.waits().type();
                processHandler(type, p);
                lastUpdatedAt = p.lastUpdatedAt();
            }
        }
    }

    private void processHandler(WaitType type, WaitingProcess p) {
        ProcessWaitHandler<AbstractWaitCondition> handler = processWaitHandlers.get(type);
        if (handler == null) {
            log.warn("processHandler ['{}'] -> handler '{}' not found", p.instanceId(), type);
            return;
        }

        if (!handler.getProcessStatuses().contains(p.status())) {
            // clear wait conditions for finished processes
            if (FINAL_STATUSES.contains(p.status())) {
                queueManager.updateWait(new ProcessKey(p.instanceId(), p.instanceCreatedAt()), null);
            }
            return;
        }

        try {
            AbstractWaitCondition originalWaits = p.waits();
            AbstractWaitCondition processedWaits = handler.process(p.instanceId(), p.status(), originalWaits);
            if (!originalWaits.equals(processedWaits)) {
                queueManager.updateWait(new ProcessKey(p.instanceId(), p.instanceCreatedAt()), processedWaits);
            }
        } catch (Exception e) {
            log.info("processHandler ['{}', '{}'] -> error", type, p, e);
        }
    }

    @Value.Immutable
    interface WaitingProcess {

        UUID instanceId();

        ProcessStatus status();

        Timestamp instanceCreatedAt();

        Timestamp lastUpdatedAt();

        AbstractWaitCondition waits();

        static ImmutableWaitingProcess.Builder builder() {
            return ImmutableWaitingProcess.builder();
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

        public List<WaitingProcess> nextWaitItems(Timestamp lastUpdatedAt, int pollLimit) {
            return txResult(tx -> {
                ProcessQueue q = PROCESS_QUEUE.as("q");
                SelectConditionStep<Record5<UUID, String, Timestamp, Timestamp, JSONB>> s = tx.select(
                        q.INSTANCE_ID,
                        q.CURRENT_STATUS,
                        q.CREATED_AT,
                        q.LAST_UPDATED_AT,
                        q.WAIT_CONDITIONS)
                        .from(q)
                        .where(q.WAIT_CONDITIONS.isNotNull());

                if (lastUpdatedAt != null) {
                    s.and(q.LAST_UPDATED_AT.greaterThan(lastUpdatedAt));
                }

                return s.orderBy(q.LAST_UPDATED_AT)
                        .limit(pollLimit)
                        .fetch(r -> WaitingProcess.builder()
                                .instanceId(r.value1())
                                .status(ProcessStatus.valueOf(r.value2()))
                                .instanceCreatedAt(r.value3())
                                .lastUpdatedAt(r.value4())
                                .waits(objectMapper.fromJSONB(r.value5(), AbstractWaitCondition.class))
                                .build());
            });
        }
    }
}
