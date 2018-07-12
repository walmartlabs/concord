package com.walmartlabs.concord.server.security.apikey;

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
import com.walmartlabs.concord.server.cfg.ApiKeyConfiguration;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.server.jooq.Tables.API_KEYS;
import static org.jooq.impl.DSL.currentTimestamp;

@Named
@Singleton
public class ApiKeyCleaner implements BackgroundTask {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyCleaner.class);

    private static final long CLEANUP_INTERVAL = TimeUnit.DAYS.toMillis(1);
    private static final long RETRY_INTERVAL = TimeUnit.SECONDS.toMillis(10);

    private final boolean enabled;
    private final CleanerDao cleanerDao;

    private Thread worker;

    @Inject
    public ApiKeyCleaner(ApiKeyConfiguration cfg, CleanerDao cleanerDao) {
        this.enabled = cfg.isExpirationEnabled();
        this.cleanerDao = cleanerDao;
    }

    @Override
    public void start() {
        if (!this.enabled) {
            log.info("start -> removal of expired API keys is disabled");
            return;
        }

        Worker w = new Worker(cleanerDao);

        this.worker = new Thread(w, "api-key-cleaner");
        this.worker.start();
    }

    @Override
    public void stop() {
        if (this.worker != null) {
            this.worker.interrupt();
            this.worker = null;
        }
    }

    private static final class Worker implements Runnable {

        private final CleanerDao cleanerDao;

        private Worker(CleanerDao cleanerDao) {
            this.cleanerDao = cleanerDao;
        }

        @Override
        public void run() {
            log.info("run -> started");

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    cleanerDao.deleteExpiredKeys();
                    sleep(CLEANUP_INTERVAL);
                } catch (Exception e) {
                    log.warn("run -> cleaning error: {}. Will retry in {}ms...", e.getMessage(), RETRY_INTERVAL);
                    sleep(RETRY_INTERVAL);
                }
            }

            log.info("run -> ended");
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

        void deleteExpiredKeys() {
            tx(tx -> {
                int keys = tx.deleteFrom(API_KEYS)
                        .where(API_KEYS.EXPIRED_AT.lessOrEqual(currentTimestamp()))
                        .execute();

                log.info("deleteExpiredKeys -> removed {} key(s)", keys);
            });
        }
    }
}
