package com.walmartlabs.concord.server.audit;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.PeriodicTask;
import com.walmartlabs.concord.server.cfg.AuditConfiguration;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.server.jooq.tables.AuditLog.AUDIT_LOG;

@Named
@Singleton
public class AuditLogCleaner extends PeriodicTask {

    private static final Logger log = LoggerFactory.getLogger(AuditLogCleaner.class);

    private static final long CLEANUP_INTERVAL = TimeUnit.HOURS.toMillis(1);
    private static final long RETRY_INTERVAL = TimeUnit.SECONDS.toMillis(10);

    private final CleanerDao cleanerDao;
    private final long maxAge;

    @Inject
    public AuditLogCleaner(CleanerDao cleanerDao, AuditConfiguration cfg) {
        super(CLEANUP_INTERVAL, RETRY_INTERVAL);
        this.cleanerDao = cleanerDao;
        this.maxAge = cfg.getMaxLogAge();
    }

    @Override
    protected void performTask() {
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - maxAge);
        cleanerDao.deleteOldLogs(cutoff);
    }

    @Named
    private static class CleanerDao extends AbstractDao {

        @Inject
        protected CleanerDao(@Named("app") Configuration cfg) {
            super(cfg);
        }

        void deleteOldLogs(Timestamp cutoff) {
            long t1 = System.currentTimeMillis();

            tx(tx -> tx.deleteFrom(AUDIT_LOG).where(AUDIT_LOG.ENTRY_DATE.lessThan(cutoff)).execute());
            long t2 = System.currentTimeMillis();
            log.info("deleteOldLogs -> removed entries older than {}, took {}ms", cutoff, (t2 - t1));
        }
    }
}
