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
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.cfg.ApiKeyConfiguration;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.server.jooq.Tables.API_KEYS;
import static org.jooq.impl.DSL.currentOffsetDateTime;

public class ApiKeyCleaner implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyCleaner.class);

    private static final long CLEANUP_INTERVAL = TimeUnit.DAYS.toSeconds(1);

    private final boolean enabled;
    private final CleanerDao cleanerDao;

    @Inject
    public ApiKeyCleaner(ApiKeyConfiguration cfg, CleanerDao cleanerDao) {
        this.enabled = cfg.isExpirationEnabled();
        this.cleanerDao = cleanerDao;
    }

    @Override
    public String getId() {
        return "api-key-cleanup";
    }

    @Override
    public long getIntervalInSec() {
        return this.enabled ? CLEANUP_INTERVAL : 0;
    }

    @Override
    public void performTask() {
        cleanerDao.deleteExpiredKeys();
    }

    @Named
    private static class CleanerDao extends AbstractDao {

        @Inject
        protected CleanerDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        void deleteExpiredKeys() {
            tx(tx -> {
                int keys = tx.deleteFrom(API_KEYS)
                        .where(API_KEYS.EXPIRED_AT.lessOrEqual(currentOffsetDateTime()))
                        .execute();

                log.info("deleteExpiredKeys -> removed {} key(s)", keys);
            });
        }
    }
}
