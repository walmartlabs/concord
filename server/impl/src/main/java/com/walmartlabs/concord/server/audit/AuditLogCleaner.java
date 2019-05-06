package com.walmartlabs.concord.server.audit;

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
import com.walmartlabs.concord.server.cfg.AuditConfiguration;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Timestamp;

import static com.walmartlabs.concord.server.jooq.tables.AuditLog.AUDIT_LOG;

@Named("audit-log-cleaner")
@Singleton
public class AuditLogCleaner implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(AuditLogCleaner.class);

    private final AuditConfiguration cfg;
    private final CleanerDao cleanerDao;

    @Inject
    public AuditLogCleaner(AuditConfiguration cfg, CleanerDao cleanerDao) {
        this.cfg = cfg;
        this.cleanerDao = cleanerDao;
    }

    @Override
    public long getIntervalInSec() {
        return cfg.getPeriod();
    }

    @Override
    public void performTask() {
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - cfg.getMaxLogAge());
        cleanerDao.deleteOldLogs(cutoff);
    }

    @Named
    private static class CleanerDao extends AbstractDao {

        @Inject
        protected CleanerDao(@MainDB Configuration cfg) {
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
