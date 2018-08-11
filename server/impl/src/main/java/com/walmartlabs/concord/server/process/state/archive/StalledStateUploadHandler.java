package com.walmartlabs.concord.server.process.state.archive;

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
import com.walmartlabs.concord.server.PeriodicTask;
import com.walmartlabs.concord.server.cfg.ProcessStateArchiveConfiguration;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Timestamp;

import static com.walmartlabs.concord.server.jooq.tables.ProcessStateArchive.PROCESS_STATE_ARCHIVE;

@Named
@Singleton
public class StalledStateUploadHandler extends PeriodicTask {

    private static final Logger log = LoggerFactory.getLogger(StalledStateUploadHandler.class);

    private final ProcessStateArchiveConfiguration cfg;
    private final CleanupDao cleanupDao;

    @Inject
    public StalledStateUploadHandler(ProcessStateArchiveConfiguration cfg, CleanupDao cleanupDao) {
        super(cfg.isEnabled() ? 60000 : 0, 30000);
        this.cfg = cfg;
        this.cleanupDao = cleanupDao;
    }

    @Override
    protected void performTask() {
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - cfg.getStalledAge());
        cleanupDao.process(cutoff);
    }

    @Named
    private static class CleanupDao extends AbstractDao {

        @Inject
        public CleanupDao(Configuration cfg) {
            super(cfg);
        }

        public void process(Timestamp cutoff) {
            tx(tx -> {
                int i = tx.deleteFrom(PROCESS_STATE_ARCHIVE)
                        .where(PROCESS_STATE_ARCHIVE.LAST_UPDATED_AT.lessOrEqual(cutoff)
                                .and(PROCESS_STATE_ARCHIVE.STATUS.eq(ArchivalStatus.IN_PROGRESS.toString())))
                        .execute();

                log.info("process -> {} record(s) removed", i);
            });
        }
    }
}
