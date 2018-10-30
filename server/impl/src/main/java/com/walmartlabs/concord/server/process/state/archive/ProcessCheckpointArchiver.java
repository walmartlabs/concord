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

import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.cfg.ProcessCheckpointArchiveConfiguration;
import com.walmartlabs.concord.server.process.state.CheckpointDao;
import com.walmartlabs.concord.server.task.ScheduledTask;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_CHECKPOINTS;
import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_CHECKPOINT_ARCHIVE;
import static com.walmartlabs.concord.server.jooq.tables.ProcessStateArchive.PROCESS_STATE_ARCHIVE;
import static org.jooq.impl.DSL.*;

@Named("process-checkpoint-archiver")
@Singleton
public class ProcessCheckpointArchiver implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ProcessCheckpointArchiver.class);

    private static final String ARCHIVE_CONTENT_TYPE = "application/zip";

    private final ProcessCheckpointArchiveConfiguration cfg;
    private final MultiStoreConnector store;
    private final CheckpointDao checkpointDao;
    private final ArchiverDao dao;
    private final ForkJoinPool forkJoinPool;

    @Inject
    public ProcessCheckpointArchiver(ProcessCheckpointArchiveConfiguration cfg,
                                     MultiStoreConnector store,
                                     CheckpointDao checkpointDao,
                                     ArchiverDao dao) {

        this.cfg = cfg;
        this.checkpointDao = checkpointDao;
        this.dao = dao;
        this.store = store;
        this.forkJoinPool = new ForkJoinPool(cfg.getUploadThreads());
    }

    public boolean isArchived(UUID checkpointId) {
        return cfg.isEnabled() && dao.isArchived(checkpointId);
    }

    public boolean export(UUID checkpointId, Path dest) throws IOException {
        String name = name(checkpointId);

        try (InputStream in = store.get(name)) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    @Override
    public long getIntervalInSec() {
        return cfg.isEnabled() ? cfg.getPeriod() : 0;
    }

    @Override
    public void performTask() throws Exception {
        while (!Thread.currentThread().isInterrupted()) {
            List<UUID> ids = dao.grabNextCheckpointId(10);

            if (ids.isEmpty()) {
                log.info("performTask -> nothing to do");
                break;
            }

            log.info("performTask -> processing {} entries...", ids.size());

            ForkJoinTask<?> t = forkJoinPool.submit(() -> ids.parallelStream().forEach(id -> {

                try (TemporaryPath tmp = IOUtils.tempFile("checkpoint", ".zip")) {
                    log.info("performTask -> exporting {}...", id);
                    checkpointDao.export(id, tmp.path());

                    long size = Files.size(tmp.path());
                    log.info("performTask -> uploading {} ({} bytes)...", id, size);

                    long t1 = System.currentTimeMillis();
                    store.put(tmp.path(), name(id), ARCHIVE_CONTENT_TYPE, size, getExpirationDate());

                    dao.markAsDone(id);
                    checkpointDao.delete(id);

                    long t2 = System.currentTimeMillis();
                    log.info("performTask -> {} done ({} ms)", id, (t2 - t1));
                } catch (Exception e) {
                    // the entry will be retried, see StalledUploadHandler
                    log.warn("performTask -> {} failed with: {}", id, e.getMessage(), e);
                }
            }));

            t.get();
        }
    }

    private Date getExpirationDate() {
        long age = cfg.getMaxArchiveAge();
        if (age <= 0) {
            return null;
        }

        Instant i = Instant.now();
        return Date.from(i.plus(age, ChronoUnit.MILLIS));
    }

    private static String name(UUID checkpointId) {
        return checkpointId + ".zip";
    }

    @Named
    private static class ArchiverDao extends AbstractDao {

        @Inject
        protected ArchiverDao(@Named("app") Configuration cfg) {
            super(cfg);
        }

        public boolean isArchived(UUID instanceId) {
            try (DSLContext tx = DSL.using(cfg)) {
                return tx.fetchExists(selectFrom(PROCESS_STATE_ARCHIVE)
                        .where(PROCESS_STATE_ARCHIVE.INSTANCE_ID.eq(instanceId)
                                .and(PROCESS_STATE_ARCHIVE.STATUS.eq(ArchivalStatus.DONE.toString()))));
            }
        }

        public List<UUID> grabNextCheckpointId(int limit) {
            return txResult(tx -> {
                List<UUID> ids = tx.select(PROCESS_CHECKPOINTS.CHECKPOINT_ID)
                        .from(PROCESS_CHECKPOINTS)
                        .where(notExists(
                                selectFrom(PROCESS_CHECKPOINT_ARCHIVE)
                                        .where(PROCESS_CHECKPOINT_ARCHIVE.CHECKPOINT_ID.eq(PROCESS_CHECKPOINTS.CHECKPOINT_ID))))
                        .limit(limit)
                        .forUpdate()
                        .skipLocked()
                        .fetch(PROCESS_CHECKPOINTS.CHECKPOINT_ID);

                if (ids.isEmpty()) {
                    return ids;
                }

                for (UUID id : ids) {
                    tx.insertInto(PROCESS_CHECKPOINT_ARCHIVE)
                            .columns(PROCESS_CHECKPOINT_ARCHIVE.CHECKPOINT_ID, PROCESS_CHECKPOINT_ARCHIVE.LAST_UPDATED_AT, PROCESS_CHECKPOINT_ARCHIVE.STATUS)
                            .values(value(id), currentTimestamp(), value(ArchivalStatus.IN_PROGRESS.toString()))
                            .execute();
                }

                return ids;
            });
        }

        public void markAsDone(UUID checkpointId) {
            tx(tx -> {
                int i = tx.update(PROCESS_CHECKPOINT_ARCHIVE)
                        .set(PROCESS_CHECKPOINT_ARCHIVE.STATUS, ArchivalStatus.DONE.toString())
                        .set(PROCESS_CHECKPOINT_ARCHIVE.LAST_UPDATED_AT, currentTimestamp())
                        .where(PROCESS_CHECKPOINT_ARCHIVE.CHECKPOINT_ID.eq(checkpointId))
                        .execute();

                if (i != 1) {
                    throw new IllegalStateException("Invalid number of rows updated: " + i + " (checkpointId: " + checkpointId + ")");
                }
            });
        }
    }
}
