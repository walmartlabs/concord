package com.walmartlabs.concord.server.process;

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
import com.walmartlabs.concord.server.cfg.ProcessStateConfiguration;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import org.jooq.Configuration;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Timestamp;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ProcessEvents.PROCESS_EVENTS;
import static com.walmartlabs.concord.server.jooq.tables.ProcessLogs.PROCESS_LOGS;
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.ProcessState.PROCESS_STATE;

@Named("process-cleaner")
@Singleton
public class ProcessCleaner implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ProcessCleaner.class);

    private static final String[] EXCLUDE_STATUSES = {
            ProcessStatus.STARTING.toString(),
            ProcessStatus.RUNNING.toString(),
            ProcessStatus.RESUMING.toString()
    };

    private final ProcessStateConfiguration cfg;
    private final CleanerDao cleanerDao;

    @Inject
    public ProcessCleaner(ProcessStateConfiguration cfg, CleanerDao cleanerDao) {
        this.cfg = cfg;
        this.cleanerDao = cleanerDao;
    }

    @Override
    public long getIntervalInSec() {
        return cfg.getCleanupInterval();
    }

    @Override
    public void performTask() {
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - cfg.getMaxStateAge());
        cleanerDao.deleteOldState(cutoff, cfg);
        cleanerDao.deleteOrphans(cfg);
    }

    @Named
    private static class CleanerDao extends AbstractDao {

        @Inject
        protected CleanerDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        void deleteOldState(Timestamp cutoff, ProcessStateConfiguration jobCfg) {
            long t1 = System.currentTimeMillis();

            tx(tx -> {
                SelectConditionStep<Record1<UUID>> ids = tx.select(PROCESS_QUEUE.INSTANCE_ID)
                        .from(PROCESS_QUEUE)
                        .where(PROCESS_QUEUE.LAST_UPDATED_AT.lessThan(cutoff)
                                .and(PROCESS_QUEUE.CURRENT_STATUS.notIn(EXCLUDE_STATUSES)));

                int queueEntries = 0;
                if (jobCfg.isQueueCleanup()) {
                    queueEntries = tx.deleteFrom(PROCESS_QUEUE)
                            .where(PROCESS_QUEUE.INSTANCE_ID.in(ids))
                            .execute();
                }

                int stateRecords = 0;
                if (jobCfg.isStateCleanup()) {
                    stateRecords = tx.deleteFrom(PROCESS_STATE)
                            .where(PROCESS_STATE.INSTANCE_ID.in(ids))
                            .execute();
                }

                int events = 0;
                if (jobCfg.isEventsCleanup()) {
                    events = tx.deleteFrom(PROCESS_EVENTS)
                            .where(PROCESS_EVENTS.INSTANCE_ID.in(ids))
                            .execute();
                }

                int logEntries = 0;
                if (jobCfg.isLogsCleanup()) {
                    logEntries = tx.deleteFrom(PROCESS_LOGS)
                            .where(PROCESS_LOGS.INSTANCE_ID.in(ids))
                            .execute();
                }

                log.info("deleteOldState -> removed older than {}: {} queue entries, {} log entries, {} state item(s), {} event(s)",
                        cutoff, queueEntries, logEntries, stateRecords, events);
            });

            long t2 = System.currentTimeMillis();
            log.info("deleteOldState -> took {}ms", (t2 - t1));
        }

        void deleteOrphans(ProcessStateConfiguration jobCfg) {
            long t1 = System.currentTimeMillis();

            tx(tx -> {
                SelectJoinStep<Record1<UUID>> alive = tx.select(PROCESS_QUEUE.INSTANCE_ID).from(PROCESS_QUEUE);

                int stateRecords = 0;
                if (jobCfg.isStateCleanup()) {
                    stateRecords = tx.deleteFrom(PROCESS_STATE)
                            .where(PROCESS_STATE.INSTANCE_ID.notIn(alive))
                            .execute();
                }

                int events = 0;
                if (jobCfg.isEventsCleanup()) {
                    events = tx.deleteFrom(PROCESS_EVENTS)
                            .where(PROCESS_EVENTS.INSTANCE_ID.notIn(alive))
                            .execute();
                }

                int logEntries = 0;
                if (jobCfg.isLogsCleanup()) {
                    logEntries = tx.deleteFrom(PROCESS_LOGS)
                            .where(PROCESS_LOGS.INSTANCE_ID.notIn(alive))
                            .execute();
                }

                log.info("deleteOrphans -> removed orphan data: {} log entries, {} state item(s), {} event(s)",
                        logEntries, stateRecords, events);
            });

            long t2 = System.currentTimeMillis();
            log.info("deleteOrphans -> took {}ms", (t2 - t1));
        }
    }
}
