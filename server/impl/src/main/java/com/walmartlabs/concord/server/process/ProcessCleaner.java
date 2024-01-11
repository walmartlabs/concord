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
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.server.cfg.ProcessConfiguration;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import org.jooq.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.jooq.tables.ProcessCheckpoints.PROCESS_CHECKPOINTS;
import static com.walmartlabs.concord.server.jooq.tables.ProcessEvents.PROCESS_EVENTS;
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.ProcessState.PROCESS_STATE;

public class ProcessCleaner implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ProcessCleaner.class);

    private static final String[] EXCLUDE_STATUSES = {
            ProcessStatus.STARTING.toString(),
            ProcessStatus.RUNNING.toString(),
            ProcessStatus.RESUMING.toString()
    };

    private final ProcessConfiguration cfg;
    private final CleanerDao cleanerDao;

    @Inject
    public ProcessCleaner(ProcessConfiguration cfg, @MainDB Configuration dbCfg) {
        this.cfg = cfg;
        this.cleanerDao = new CleanerDao(dbCfg);
    }

    @Override
    public String getId() {
        return "process-cleaner";
    }

    @Override
    public long getIntervalInSec() {
        return cfg.getCleanupInterval().getSeconds();
    }

    @Override
    public void performTask() {
        cleanerDao.deleteOldState(cfg);
        cleanerDao.deleteOrphans(cfg);
    }

    private static class CleanerDao extends AbstractDao {

        private CleanerDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        void deleteOldState(ProcessConfiguration jobCfg) {
            long t1 = System.currentTimeMillis();

            Field<OffsetDateTime> cutoff = PgUtils.nowMinus(jobCfg.getMaxStateAge());

            tx(tx -> {
                SelectConditionStep<Record1<UUID>> ids = tx.select(PROCESS_QUEUE.INSTANCE_ID)
                        .from(PROCESS_QUEUE)
                        .where(PROCESS_QUEUE.LAST_UPDATED_AT.lessThan(cutoff)
                                .and(PROCESS_QUEUE.CURRENT_STATUS.notIn(EXCLUDE_STATUSES)));

                int stateRecords = 0;
                int initialStateRecords = 0;
                if (jobCfg.isStateCleanup()) {
                    stateRecords = tx.deleteFrom(PROCESS_STATE)
                            .where(PROCESS_STATE.INSTANCE_ID.in(ids))
                            .execute();

                    initialStateRecords = tx.deleteFrom(PROCESS_INITIAL_STATE)
                            .where(PROCESS_INITIAL_STATE.INSTANCE_ID.in(ids))
                            .execute();
                }

                int events = 0;
                if (jobCfg.isEventsCleanup()) {
                    events = tx.deleteFrom(PROCESS_EVENTS)
                            .where(PROCESS_EVENTS.INSTANCE_ID.in(ids))
                            .execute();
                }

                int logDataEntries = 0;
                int logSegmentEntries = 0;
                if (jobCfg.isLogsCleanup()) {
                    logDataEntries = tx.deleteFrom(PROCESS_LOG_DATA)
                            .where(PROCESS_LOG_DATA.INSTANCE_ID.in(ids))
                            .execute();

                    logSegmentEntries = tx.deleteFrom(PROCESS_LOG_SEGMENTS)
                            .where(PROCESS_LOG_SEGMENTS.INSTANCE_ID.in(ids))
                            .execute();
                }

                int checkpoints = 0;
                if (jobCfg.isCheckpointCleanup()) {
                    checkpoints = tx.deleteFrom(PROCESS_CHECKPOINTS)
                            .where(PROCESS_CHECKPOINTS.INSTANCE_ID.in(ids))
                            .execute();
                }

                int queueEntries = 0;
                if (jobCfg.isQueueCleanup()) {
                    tx.deleteFrom(PROCESS_WAIT_CONDITIONS)
                            .where(PROCESS_WAIT_CONDITIONS.INSTANCE_ID.in(ids))
                            .execute();

                    queueEntries = tx.deleteFrom(PROCESS_QUEUE)
                            .where(PROCESS_QUEUE.INSTANCE_ID.in(ids))
                            .execute();
                }

                log.info("deleteOldState -> removed older than {}: {} queue entries, {} log data entries, {} log segments, {} state item(s), {} initial state item(s), {} event(s), {} checkpoint(s)",
                        jobCfg.getMaxStateAge(), queueEntries, logDataEntries, logSegmentEntries, stateRecords, initialStateRecords, events, checkpoints);
            });

            long t2 = System.currentTimeMillis();
            log.info("deleteOldState -> took {}ms", (t2 - t1));
        }

        void deleteOrphans(ProcessConfiguration jobCfg) {
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

                int checkpoints = 0;
                if (jobCfg.isCheckpointCleanup()) {
                    checkpoints = tx.deleteFrom(PROCESS_CHECKPOINTS)
                            .where(PROCESS_CHECKPOINTS.INSTANCE_ID.notIn(alive))
                            .execute();
                }

                int logDataEntries = 0;
                int logSegmentEntries = 0;
                if (jobCfg.isLogsCleanup()) {
                    logDataEntries = tx.deleteFrom(PROCESS_LOG_DATA)
                            .where(PROCESS_LOG_DATA.INSTANCE_ID.notIn(alive))
                            .execute();

                    logSegmentEntries = tx.deleteFrom(PROCESS_LOG_SEGMENTS)
                            .where(PROCESS_LOG_SEGMENTS.INSTANCE_ID.notIn(alive))
                            .execute();
                }

                log.info("deleteOrphans -> removed orphan data: {} log data entries, {} log segments, {} state item(s), {} event(s), {} checkpoint(s)",
                        logDataEntries, logSegmentEntries, stateRecords, events, checkpoints);
            });

            long t2 = System.currentTimeMillis();
            log.info("deleteOrphans -> took {}ms", (t2 - t1));
        }
    }
}
