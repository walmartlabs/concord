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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.PeriodicTask;
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.cfg.ProcessStateArchiveConfiguration;
import com.walmartlabs.concord.server.process.ProcessStatus;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.ProcessStateArchive.PROCESS_STATE_ARCHIVE;
import static com.walmartlabs.concord.server.process.state.ProcessStateManager.zipTo;
import static org.jooq.impl.DSL.*;

@Named
@Singleton
public class ProcessStateArchiver extends PeriodicTask {

    private static final Logger log = LoggerFactory.getLogger(ProcessStateArchiver.class);

    private static final ProcessStatus[] ALLOWED_STATUSES = {ProcessStatus.CANCELLED, ProcessStatus.FAILED, ProcessStatus.FINISHED};
    private static final String ARCHIVE_CONTENT_TYPE = "application/zip";

    private final ProcessStateArchiveConfiguration cfg;
    private final ProcessStateManager stateManager;
    private final MultiStoreConnector store;
    private final ArchiverDao dao;
    private final ForkJoinPool forkJoinPool;

    @Inject
    public ProcessStateArchiver(ProcessStateArchiveConfiguration cfg,
                                ProcessStateManager stateManager,
                                MultiStoreConnector store,
                                ArchiverDao dao) {

        super(cfg.getPeriod(), 30000);

        this.cfg = cfg;
        this.dao = dao;
        this.stateManager = stateManager;
        this.store = store;
        this.forkJoinPool = new ForkJoinPool(cfg.getUploadThreads());
    }

    public void export(UUID instanceId, OutputStream out) throws IOException {
        String name = name(instanceId);

        if (cfg.isEnabled() && dao.isArchived(instanceId)) {
            try (InputStream in = store.get(name)) {
                IOUtils.copy(in, out);
            }
        } else {
            try (ZipArchiveOutputStream dst = new ZipArchiveOutputStream(out)) {
                stateManager.export(instanceId, zipTo(dst));
            }
        }
    }

    @Override
    public void start() {
        if (!cfg.isEnabled()) {
            log.info("start -> state archiving is disabled");
            return;
        }

        super.start();
    }

    @Override
    protected void performTask() throws Exception {
        while (!Thread.currentThread().isInterrupted()) {
            Timestamp ageCutoff = new Timestamp(System.currentTimeMillis() - cfg.getProcessAge());
            List<UUID> ids = dao.grabNext(ALLOWED_STATUSES, ageCutoff, 10);

            if (ids.isEmpty()) {
                log.info("performTask -> nothing to do");
                break;
            }

            log.info("performTask -> processing {} entries...", ids.size());
            ForkJoinTask<?> t = forkJoinPool.submit(() -> ids.parallelStream()
                    .forEach(this::upload));

            t.get();
        }
    }

    private void upload(UUID id) {
        Path tmp = null;
        try {
            tmp = IOUtils.createTempFile("archive", ".zip");
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(tmp))) {
                log.info("performTask -> exporting {}...", id);
                stateManager.export(id, ProcessStateManager.zipTo(zip));
            }

            long size = Files.size(tmp);
            log.info("performTask -> uploading {} ({} bytes)...", id, size);

            long t1 = System.currentTimeMillis();
            store.put(tmp, name(id), ARCHIVE_CONTENT_TYPE, size, getExpirationDate());

            dao.markAsDone(id);
            stateManager.delete(id);

            long t2 = System.currentTimeMillis();
            log.info("performTask -> {} done ({} ms)", id, (t2 - t1));
        } catch (Exception e) {
            // the entry will be retried, see StalledUploadHandler
            log.warn("performTask -> {} failed with: {}", id, e.getMessage(), e);
        } finally {
            if (tmp != null) {
                try {
                    Files.delete(tmp);
                } catch (IOException e) {
                    log.warn("performTask -> can't remove the temporary file {}: {}", tmp, e.getMessage());
                }
            }
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

    private static String name(UUID instanceId) {
        return instanceId + ".zip";
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

        public List<UUID> grabNext(ProcessStatus[] statuses, Timestamp ageCutoff, int limit) {
            return txResult(tx -> {
                List<UUID> ids = tx.select(PROCESS_QUEUE.INSTANCE_ID)
                        .from(PROCESS_QUEUE)
                        .where(PROCESS_QUEUE.CURRENT_STATUS.in(Utils.toString(statuses))
                                .and(PROCESS_QUEUE.LAST_UPDATED_AT.lessOrEqual(ageCutoff))
                                .andNotExists(selectFrom(PROCESS_STATE_ARCHIVE)
                                        .where(PROCESS_STATE_ARCHIVE.INSTANCE_ID.eq(PROCESS_QUEUE.INSTANCE_ID))))
                        .limit(limit)
                        .forUpdate()
                        .skipLocked()
                        .fetch(PROCESS_QUEUE.INSTANCE_ID);

                if (ids.isEmpty()) {
                    return ids;
                }

                for (UUID id : ids) {
                    tx.insertInto(PROCESS_STATE_ARCHIVE)
                            .columns(PROCESS_STATE_ARCHIVE.INSTANCE_ID, PROCESS_STATE_ARCHIVE.LAST_UPDATED_AT, PROCESS_STATE_ARCHIVE.STATUS)
                            .values(value(id), currentTimestamp(), value(ArchivalStatus.IN_PROGRESS.toString()))
                            .execute();
                }

                return ids;
            });
        }

        public void markAsDone(UUID instanceId) {
            tx(tx -> {
                int i = tx.update(PROCESS_STATE_ARCHIVE)
                        .set(PROCESS_STATE_ARCHIVE.STATUS, ArchivalStatus.DONE.toString())
                        .set(PROCESS_STATE_ARCHIVE.LAST_UPDATED_AT, currentTimestamp())
                        .where(PROCESS_STATE_ARCHIVE.INSTANCE_ID.eq(instanceId))
                        .execute();

                if (i != 1) {
                    throw new IllegalStateException("Invalid number of rows updated: " + i + " (instanceId: " + instanceId + ")");
                }
            });
        }
    }
}
