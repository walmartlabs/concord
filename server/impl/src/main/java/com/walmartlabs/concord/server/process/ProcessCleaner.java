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
import com.walmartlabs.concord.server.BackgroundTask;
import com.walmartlabs.concord.server.cfg.ProcessStateConfiguration;
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
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.server.jooq.tables.ProcessEvents.PROCESS_EVENTS;
import static com.walmartlabs.concord.server.jooq.tables.ProcessLogs.PROCESS_LOGS;
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.ProcessState.PROCESS_STATE;

@Named
@Singleton
public class ProcessCleaner implements BackgroundTask {

    private static final Logger log = LoggerFactory.getLogger(ProcessCleaner.class);

    private static final long CLEANUP_INTERVAL = TimeUnit.HOURS.toMillis(1);
    private static final long RETRY_INTERVAL = TimeUnit.SECONDS.toMillis(10);

    private static final String[] EXCLUDE_STATUSES = {
            ProcessStatus.STARTING.toString(),
            ProcessStatus.RUNNING.toString(),
            ProcessStatus.RESUMING.toString()
    };

    private final CleanerDao cleanerDao;
    private final ProcessStateConfiguration cfg;

    private Thread worker;

    @Inject
    public ProcessCleaner(CleanerDao cleanerDao, ProcessStateConfiguration cfg) {
        this.cleanerDao = cleanerDao;
        this.cfg = cfg;
    }

    @Override
    public void start() {
        Worker w = new Worker(cleanerDao, cfg.getMaxStateAge());

        this.worker = new Thread(w, "process-state-cleaner");
        this.worker.start();
    }

    @Override
    public void stop() {
        if (this.worker != null) {
            this.worker.interrupt();
        }
    }

    private static final class Worker implements Runnable {

        private final CleanerDao cleanerDao;
        private final long maxAge;

        private Worker(CleanerDao cleanerDao, long maxAge) {
            this.cleanerDao = cleanerDao;
            this.maxAge = maxAge;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Timestamp cutoff = new Timestamp(System.currentTimeMillis() - maxAge);
                    cleanerDao.deleteOldState(cutoff);
                    cleanerDao.deleteOrphans();
                    sleep(CLEANUP_INTERVAL);
                } catch (Exception e) {
                    log.warn("run -> state cleaning error: {}. Will retry in {}ms...", e.getMessage(), RETRY_INTERVAL);
                    sleep(RETRY_INTERVAL);
                }
            }
        }

        private static void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Named
    private static class CleanerDao extends AbstractDao {

        @Inject
        protected CleanerDao(Configuration cfg) {
            super(cfg);
        }

        void deleteOldState(Timestamp cutoff) {
            long t1 = System.currentTimeMillis();

            tx(tx -> {
                SelectConditionStep<Record1<UUID>> ids = tx.select(PROCESS_QUEUE.INSTANCE_ID)
                        .from(PROCESS_QUEUE)
                        .where(PROCESS_QUEUE.LAST_UPDATED_AT.lessThan(cutoff)
                                .and(PROCESS_QUEUE.CURRENT_STATUS.notIn(EXCLUDE_STATUSES)));

                int queueEntries = tx.deleteFrom(PROCESS_QUEUE)
                        .where(PROCESS_QUEUE.INSTANCE_ID.in(ids))
                        .execute();

                int stateRecords = tx.deleteFrom(PROCESS_STATE)
                        .where(PROCESS_STATE.INSTANCE_ID.in(ids))
                        .execute();

                int events = tx.deleteFrom(PROCESS_EVENTS)
                        .where(PROCESS_EVENTS.INSTANCE_ID.in(ids))
                        .execute();

                int logEntries = tx.deleteFrom(PROCESS_LOGS)
                        .where(PROCESS_LOGS.INSTANCE_ID.in(ids))
                        .execute();

                log.info("deleteOldState -> removed older than {}: {} queue entries, {} log entries, {} state item(s), {} event(s)",
                        cutoff, queueEntries, logEntries, stateRecords, events);
            });

            long t2 = System.currentTimeMillis();
            log.info("deleteOldState -> took {}ms", (t2 - t1));
        }

        void deleteOrphans() {
            long t1 = System.currentTimeMillis();

            tx(tx -> {
                SelectJoinStep<Record1<UUID>> alive = tx.select(PROCESS_QUEUE.INSTANCE_ID).from(PROCESS_QUEUE);

                int stateRecords = tx.deleteFrom(PROCESS_STATE)
                        .where(PROCESS_STATE.INSTANCE_ID.notIn(alive))
                        .execute();

                int events = tx.deleteFrom(PROCESS_EVENTS)
                        .where(PROCESS_EVENTS.INSTANCE_ID.notIn(alive))
                        .execute();

                int logEntries = tx.deleteFrom(PROCESS_LOGS)
                        .where(PROCESS_LOGS.INSTANCE_ID.notIn(alive))
                        .execute();

                log.info("deleteOrphans -> removed orphan data: {} log entries, {} state item(s), {} event(s)",
                        logEntries, stateRecords, events);
            });

            long t2 = System.currentTimeMillis();
            log.info("deleteOrphans -> took {}ms", (t2 - t1));
        }
    }
}
