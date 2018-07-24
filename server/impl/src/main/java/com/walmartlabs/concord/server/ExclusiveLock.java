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
import com.walmartlabs.concord.server.jooq.tables.records.TaskLocksRecord;
import org.jooq.Configuration;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;
import java.util.concurrent.ThreadLocalRandom;

import static com.walmartlabs.concord.server.jooq.tables.TaskLocks.TASK_LOCKS;
import static org.jooq.impl.DSL.currentTimestamp;

@Named
public class ExclusiveLock {

    private static final Logger log = LoggerFactory.getLogger(ExclusiveLock.class);

    private final ExclusiveLockDao dao;

    @Inject
    public ExclusiveLock(ExclusiveLockDao dao) {
        this.dao = dao;
    }

    public void withTryLock(String lockKey, Runnable f) {
        Lock lock = dao.tryLock(lockKey);
        if (lock == null) {
            log.info("withTryLock ['{}'] -> already locked", lockKey);
            return;
        }

        try {
            f.run();
        } finally {
            dao.unlock(lock);
        }
    }

    private static class Lock {

        private final String key;
        private final int cnt;

        public Lock(String key, int cnt) {
            this.key = key;
            this.cnt = cnt;
        }
    }

    @Named
    private static class ExclusiveLockDao extends AbstractDao {

        @Inject
        public ExclusiveLockDao(@Named("app") Configuration cfg) {
            super(cfg);
        }

        public Lock tryLock(String lockKey) {
            return txResult(tx -> {
                TaskLocksRecord r = tx.selectFrom(TASK_LOCKS)
                        .where(TASK_LOCKS.LOCK_KEY.eq(lockKey))
                        .limit(1)
                        .forUpdate()
                        .skipLocked()
                        .fetchOne();

                if (r == null) {
                    throw new RuntimeException("Lock table without a record: " + lockKey);
                }

                if (Boolean.TRUE.equals(r.getLocked())) {
                    return null;
                }

                int cnt = ThreadLocalRandom.current().nextInt();

                tx.update(TASK_LOCKS)
                        .set(TASK_LOCKS.LOCKED, true)
                        .set(TASK_LOCKS.LOCKED_AT, currentTimestamp())
                        .set(TASK_LOCKS.LOCK_COUNTER, cnt)
                        .where(TASK_LOCKS.LOCK_KEY.eq(lockKey))
                        .execute();

                return new Lock(lockKey, cnt);
            });
        }

        public void unlock(Lock lock) {
            tx(tx -> {
                int i = tx.update(TASK_LOCKS)
                        .set(TASK_LOCKS.LOCKED, false)
                        .set(TASK_LOCKS.LOCKED_AT, (Timestamp) null)
                        .set(TASK_LOCKS.LOCK_COUNTER, 0)
                        .where(TASK_LOCKS.LOCK_KEY.eq(lock.key).and(TASK_LOCKS.LOCK_COUNTER.eq(lock.cnt)))
                        .execute();

                if (i != 1) {
                    throw new DataAccessException("Invalid number of rows updated: " + i + ". Possible " +
                            "incorrect usage of TASK_LOCKS. Lock key: " + lock.key);
                }
            });
        }
    }
}
