package com.walmartlabs.concord.server;

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
import org.jooq.Configuration;
import org.jooq.Record4;
import org.jooq.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;
import java.util.Date;

import static org.jooq.impl.DSL.currentTimestamp;

@Named
public class ExclusiveLock {

    private static final Logger log = LoggerFactory.getLogger(ExclusiveLock.class);

    private final ExclusiveLockDao dao;

    @Inject
    public ExclusiveLock(ExclusiveLockDao dao) {
        this.dao = dao;
    }

    public LockInfo withTryLock(Table<? extends Record4<Integer, Boolean, String, Timestamp>> lockTable, Runnable f) {
        return withTryLock(null, lockTable, f);
    }

    public LockInfo withTryLock(String lockedBy, Table<? extends Record4<Integer, Boolean, String, Timestamp>> lockTable, Runnable f) {
        LockInfo lockInfo = dao.tryLock(lockedBy, lockTable);
        if (lockInfo != null) {
            log.info("withTryLock ['{}', '{}'] -> already locked by {} at {}", lockedBy, lockTable.getName(), lockInfo.getLockedBy(), lockInfo.getLockedAt());
            return lockInfo;
        }

        try {
            f.run();
            return null;
        } finally {
            dao.unlock(lockTable);
        }
    }

    public static class LockInfo {
        private final String lockedBy;
        private final Date lockedAt;

        public LockInfo(String lockedBy, Date lockedAt) {
            this.lockedBy = lockedBy;
            this.lockedAt = lockedAt;
        }

        public String getLockedBy() {
            return lockedBy;
        }

        public Date getLockedAt() {
            return lockedAt;
        }

        @Override
        public String toString() {
            return "LockInfo{" +
                    "lockedBy='" + lockedBy + '\'' +
                    ", lockedAt=" + lockedAt +
                    '}';
        }
    }

    @Named
    static class ExclusiveLockDao extends AbstractDao {

        private static final int LOCK_ID = 1;

        @Inject
        public ExclusiveLockDao(Configuration cfg) {
            super(cfg);
        }

        public LockInfo tryLock(String lockedBy, Table<? extends Record4<Integer, Boolean, String, Timestamp>> lockTable) {
            return txResult(tx -> {
                Record4<Integer, Boolean, String, Timestamp> r = tx.selectFrom(lockTable)
                        .where(lockTable.field(0, Integer.class).eq(LOCK_ID))
                        .limit(1)
                        .forUpdate()
                        .skipLocked()
                        .fetchOne();

                if (r == null) {
                    throw new RuntimeException("lock table without record");
                }

                // locked?
                if (r.value2()) {
                    return new LockInfo(r.value3(), r.value4());
                }

                tx.update(lockTable)
                    .set(lockTable.field(1, Boolean.class), true)
                    .set(lockTable.field(2, String.class), lockedBy)
                    .set(lockTable.field(3, Timestamp.class), currentTimestamp())
                    .where(lockTable.field(0, Integer.class).eq(LOCK_ID))
                    .execute();

                return null;
            });
        }

        public void unlock(Table<? extends Record4<Integer, Boolean, String, Timestamp>> lockTable) {
            tx(tx -> tx.update(lockTable)
                    .set(lockTable.field(1, Boolean.class), false)
                    .set(lockTable.field(2, String.class), (String)null)
                    .set(lockTable.field(3, Timestamp.class), (Timestamp)null)
                    .where(lockTable.field(0, Integer.class).eq(LOCK_ID))
                    .execute());
        }
    }
}
