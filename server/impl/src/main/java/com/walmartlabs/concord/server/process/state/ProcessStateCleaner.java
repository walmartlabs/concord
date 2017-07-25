package com.walmartlabs.concord.server.process.state;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import org.eclipse.sisu.EagerSingleton;
import org.jooq.Configuration;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.ProcessState.PROCESS_STATE;

@Named
@EagerSingleton
public class ProcessStateCleaner {

    private static final Logger log = LoggerFactory.getLogger(ProcessStateCleaner.class);

    private static final long CLEANUP_INTERVAL = TimeUnit.HOURS.toMillis(1);
    private static final long RETRY_INTERVAL = TimeUnit.SECONDS.toMillis(10);
    private static final long AGE_CUTOFF = TimeUnit.DAYS.toMillis(3);

    private static final String[] REMOVE_STATUSES = {
            ProcessStatus.FINISHED.toString(),
            ProcessStatus.FAILED.toString()
    };

    @Inject
    public ProcessStateCleaner(CleanerDao cleanerDao) {
        init(cleanerDao);
    }

    private final void init(CleanerDao cleanerDao) {
        Worker w = new Worker(cleanerDao);

        Thread t = new Thread(w, "process-state-cleaner");
        t.start();
    }

    private static final class Worker implements Runnable {

        private final CleanerDao cleanerDao;

        private Worker(CleanerDao cleanerDao) {
            this.cleanerDao = cleanerDao;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Timestamp cutoff = new Timestamp(System.currentTimeMillis() - AGE_CUTOFF);
                    cleanerDao.deleteOldState(cutoff);
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
                Thread.currentThread().isInterrupted();
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
            tx(tx -> {
                SelectConditionStep<Record1<UUID>> ids = tx.select(PROCESS_QUEUE.INSTANCE_ID)
                        .from(PROCESS_QUEUE)
                        .where(PROCESS_QUEUE.LAST_UPDATED_AT.lessThan(cutoff)
                                .and(PROCESS_QUEUE.CURRENT_STATUS.in(REMOVE_STATUSES)));

                int rows = tx.deleteFrom(PROCESS_STATE)
                        .where(PROCESS_STATE.INSTANCE_ID.in(ids))
                        .execute();

                log.info("deleteOldState -> removed {} items(s) older than {}", rows, cutoff);
            });
        }
    }
}
