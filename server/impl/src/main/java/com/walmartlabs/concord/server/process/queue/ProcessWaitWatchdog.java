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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.ProcessStatus;
import com.walmartlabs.concord.server.task.ScheduledTask;
import org.immutables.value.Value;
import org.jooq.Configuration;
import org.jooq.Record5;
import org.jooq.SelectConditionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;

/**
 * Takes care of processes with wait conditions.
 * E.g. waiting for other processes to finish, locking, etc.
 */
@Named("process-wait-watchdog")
@Singleton
public class ProcessWaitWatchdog implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ProcessWaitWatchdog.class);

    private final WatchdogDao dao;
    private final ProcessQueueDao processQueueDao;
    private final Map<WaitType, ProcessWaitHandler<AbstractWaitCondition>> processWaitHandlers;

    @Inject
    @SuppressWarnings("unchecked")
    public ProcessWaitWatchdog(WatchdogDao dao, ProcessQueueDao processQueueDao, Set<ProcessWaitHandler> handlers) {
        this.dao = dao;
        this.processQueueDao = processQueueDao;
        this.processWaitHandlers = new HashMap<>();

        handlers.forEach(h -> this.processWaitHandlers.put(h.getType(), h));
    }

    @Override
    public long getIntervalInSec() {
        // TODO cfg?
        return 5;
    }

    @Override
    public void performTask() {
        Timestamp lastUpdatedAt = null;
        while (true) {
            WaitingProcess p = dao.nextWaitItem(lastUpdatedAt);
            if (p == null) {
                return;
            }

            WaitType type = p.waits().type();
            processHandler(type, p);
            lastUpdatedAt = p.lastUpdatedAt();
        }
    }

    private void processHandler(WaitType type, WaitingProcess p) {
        ProcessWaitHandler<AbstractWaitCondition> handler = processWaitHandlers.get(type);
        if (handler == null) {
            log.warn("performTask ['{}'] -> handler '{}' not found", p.instanceId(), type);
            return;
        }

        try {
            AbstractWaitCondition originalWaits = p.waits();
            AbstractWaitCondition processedWaits = handler.process(p.instanceId(), p.status(), originalWaits);
            if (!originalWaits.equals(processedWaits)) {
                processQueueDao.updateWait(new ProcessKey(p.instanceId(), p.instanceCreatedAt()), processedWaits);
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

        private final ObjectMapper objectMapper;

        @Inject
        public WatchdogDao(@Named("app") Configuration cfg, ObjectMapper objectMapper) {
            super(cfg);

            this.objectMapper = objectMapper;
        }

        public WaitingProcess nextWaitItem(Timestamp lastUpdatedAt) {
            return txResult(tx -> {
                ProcessQueue q = PROCESS_QUEUE.as("q");
                SelectConditionStep<Record5<UUID, String, Timestamp, Timestamp, Object>> s = tx.select(
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
                        .limit(1)
                        .fetchOne(r -> WaitingProcess.builder()
                                .instanceId(r.value1())
                                .status(ProcessStatus.valueOf(r.value2()))
                                .instanceCreatedAt(r.value3())
                                .lastUpdatedAt(r.value4())
                                .waits(deserialize(r.value5()))
                                .build());
            });
        }

        private AbstractWaitCondition deserialize(Object o) {
            if (o == null) {
                return null;
            }

            try {
                return objectMapper.readValue(String.valueOf(o), AbstractWaitCondition.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
