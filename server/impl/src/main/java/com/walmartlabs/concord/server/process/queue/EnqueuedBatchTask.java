package com.walmartlabs.concord.server.process.queue;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.repository.Repository;
import com.walmartlabs.concord.server.PeriodicTask;
import com.walmartlabs.concord.server.cfg.EnqueueWorkersConfiguration;
import com.walmartlabs.concord.server.process.PartialProcessKey;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadBuilder;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.pipelines.EnqueueProcessPipeline;
import com.walmartlabs.concord.server.process.pipelines.processors.Pipeline;
import com.walmartlabs.concord.server.repository.RepositoryManager;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.immutables.value.Value;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.Tables.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static org.jooq.impl.DSL.value;

public class EnqueuedBatchTask extends PeriodicTask {

    private static final Logger log = LoggerFactory.getLogger(EnqueuedBatchTask.class);

    private static final long ERROR_RETRY_INTERVAL = TimeUnit.SECONDS.toMillis(5);

    private final Dao dao;
    private final EnqueueWorkersConfiguration cfg;
    private final Histogram batchHistogram;

    private final ExecutorService executor;
    private final BlockingQueue<Batch> queue;
    private final List<String> inflightRepoUrls;
    private final AtomicInteger freeWorkersCount;

    @Inject
    public EnqueuedBatchTask(Dao dao,
                             EnqueueWorkersConfiguration cfg,
                             EnqueueProcessPipeline pipeline,
                             RepositoryManager repositoryManager,
                             MetricRegistry metricRegistry) {

        super(cfg.getInterval(), ERROR_RETRY_INTERVAL);

        this.cfg = cfg;
        this.dao = dao;
        this.batchHistogram = metricRegistry.histogram("enqueued-task-batches-histogram");

        this.queue = new ArrayBlockingQueue<>(cfg.getWorkersCount());
        this.freeWorkersCount = new AtomicInteger(cfg.getWorkersCount());
        this.inflightRepoUrls = Collections.synchronizedList(new ArrayList<>(cfg.getWorkersCount()));

        this.executor = Executors.newFixedThreadPool(cfg.getWorkersCount());
        for (int i = 0; i < cfg.getWorkersCount(); i++) {
            this.executor.submit(new Worker(pipeline, repositoryManager, queue));
        }

        metricRegistry.gauge("enqueued-workers-available", () -> freeWorkersCount::get);
        metricRegistry.gauge("enqueued-inflight-urls", () -> inflightRepoUrls::size);
    }

    @Override
    public void stop() {
        super.stop();
        executor.shutdown();
    }

    @Override
    @WithTimer
    protected boolean performTask() {
        if (freeWorkersCount.get() == 0) {
            return false;
        }

        int limit = Math.min(freeWorkersCount.get(), cfg.getWorkersCount());

        List<String> ignoreRepoUrls;
        synchronized (inflightRepoUrls) {
            ignoreRepoUrls = new ArrayList<>(inflightRepoUrls);
        }

        Collection<Batch> batches = dao.poll(ignoreRepoUrls, limit);
        if (batches.isEmpty()) {
            return false;
        }

        for (Batch b : batches) {
            if (b.repoId() != null) {
                List<ProcessKey> k = dao.poll(b.repoId(), cfg.getBatchSize());
                b.keys().addAll(k);
            }

            batchHistogram.update(b.keys().size());
        }

        for (int i = 0; i < batches.size(); i++) {
            freeWorkersCount.decrementAndGet();
        }

        for (Batch b : batches) {
            if (b.repoUrl() != null) {
                inflightRepoUrls.add(b.repoUrl());
            }
        }

        queue.addAll(batches);

        return freeWorkersCount.get() > 0;
    }

    private void onWorkerFree(String repoUrl) {
        freeWorkersCount.incrementAndGet();
        if (repoUrl != null) {
            inflightRepoUrls.remove(repoUrl);
        }
    }

    private class Worker implements Runnable {

        private final Pipeline pipeline;
        private final RepositoryManager repositoryManager;
        private final BlockingQueue<Batch> queue;

        private Worker(Pipeline pipeline, RepositoryManager repositoryManager, BlockingQueue<Batch> queue) {
            this.pipeline = pipeline;
            this.repositoryManager = repositoryManager;
            this.queue = queue;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                String repoUrl = null;
                try {
                    Batch keys = queue.take();
                    repoUrl = keys.repoUrl();
                    startProcessBatch(keys);
                } catch (Exception e) {
                    log.error("run -> error", e);
                    sleep(ERROR_RETRY_INTERVAL);
                } finally {
                    onWorkerFree(repoUrl);
                }
            }
        }

        private void startProcessBatch(Batch batch) {
            try {
                if (batch.repoUrl() != null && batch.keys().size() > 1) {
                    repositoryManager.withLock(batch.repoUrl(), () -> {
                        Repository repository = null;
                        for (ProcessKey key : batch.keys()) {
                            Payload payload = startProcess(key, repository);
                            if (payload != null && repository == null) {
                                repository = payload.getHeader(Payload.REPOSITORY);
                            }
                        }
                        return null;
                    });
                } else {
                    for (ProcessKey key : batch.keys()) {
                        startProcess(key, null);
                    }
                }
            } catch (Exception e) {
                log.error("startProcessBatch ['{}'] -> error", batch, e);
            }
        }

        private Payload startProcess(ProcessKey key, Repository repository) {
            try {
                Payload payload = PayloadBuilder.start(key).build();
                if (repository != null) {
                    payload = payload.putHeader(Payload.REPOSITORY, repository);
                }
                return pipeline.process(payload);
            } catch (Exception e) {
                log.error("startProcess ['{}'] -> error", key, e);
            }
            return null;
        }
    }

    static class Batch {

        public static Batch key(ProcessKey key) {
            return new Batch(null, null, Collections.singletonList(key));
        }

        private final UUID repoId;
        private final String repoUrl;
        private final List<ProcessKey> keys;

        public Batch(UUID repoId, String repoUrl) {
            this(repoId, repoUrl, new ArrayList<>());
        }

        private Batch(UUID repoId, String repoUrl, List<ProcessKey> keys) {
            this.repoId = repoId;
            this.repoUrl = repoUrl;
            this.keys = keys;
        }

        public UUID repoId() {
            return repoId;
        }

        public String repoUrl() {
            return repoUrl;
        }

        public List<ProcessKey> keys() {
            return keys;
        }
    }

    @Value.Immutable
    interface ProcessItem {

        @Value.Parameter
        ProcessKey key();

        @Nullable
        @Value.Parameter
        UUID repoId();

        @Nullable
        @Value.Parameter
        String repoUrl();

        static ProcessItem of(ProcessKey key, UUID repoId, String repoUrl) {
            return ImmutableProcessItem.of(key, repoId, repoUrl);
        }
    }

    @Named
    static class Dao extends AbstractDao {

        @Inject
        public Dao(@MainDB Configuration cfg) {
            super(cfg);
        }

        @WithTimer
        public Collection<Batch> poll(List<String> ignoreRepoUrls, int limit) {
            return txResult(tx -> {
                List<ProcessItem> items = tx.select(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.CREATED_AT, PROCESS_QUEUE.REPO_ID, REPOSITORIES.REPO_URL)
                        .from(PROCESS_QUEUE).leftJoin(REPOSITORIES).on(REPOSITORIES.REPO_ID.eq(PROCESS_QUEUE.REPO_ID))
                        .where(PROCESS_QUEUE.CURRENT_STATUS.eq(ProcessStatus.NEW.name())
                                .and(REPOSITORIES.REPO_URL.isNull().or(REPOSITORIES.REPO_URL.notIn(ignoreRepoUrls))))
                        .orderBy(PROCESS_QUEUE.CREATED_AT)
                        .limit(limit)
                        .forUpdate().of(PROCESS_QUEUE)
                        .skipLocked()
                        .fetch(r -> ProcessItem.of(new ProcessKey(r.value1(), r.value2()), r.value3(), r.value4()));

                if (items.isEmpty()) {
                    return Collections.emptyList();
                }

                Collection<Batch> batches = toBatches(items);
                batches = removeDuplicateUrls(batches);

                toPreparing(tx, batches.stream()
                        .map(Batch::keys)
                        .flatMap(Collection::stream)
                        .map(PartialProcessKey::getInstanceId)
                        .collect(Collectors.toList()));

                return batches;
            });
        }

        @WithTimer
        public List<ProcessKey> poll(UUID repoId, int limit) {
            return txResult(tx -> {
                List<ProcessKey> result = tx.select(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.CREATED_AT)
                        .from(PROCESS_QUEUE)
                        .where(PROCESS_QUEUE.CURRENT_STATUS.eq(ProcessStatus.NEW.name())
                                .and(PROCESS_QUEUE.REPO_ID.eq(repoId)))
                        .orderBy(PROCESS_QUEUE.CREATED_AT)
                        .limit(limit)
                        .forUpdate()
                        .skipLocked()
                        .fetch(r -> new ProcessKey(r.value1(), r.value2()));

                if (result.isEmpty()) {
                    return result;
                }

                toPreparing(tx, result.stream()
                        .map(PartialProcessKey::getInstanceId)
                        .collect(Collectors.toList()));

                return result;
            });
        }

        private static Collection<Batch> toBatches(List<ProcessItem> keys) {
            List<Batch> result = new ArrayList<>();

            Map<UUID, Batch> batchByUrl = new HashMap<>();
            for (ProcessItem item : keys) {
                if (item.repoId() == null) {
                    result.add(Batch.key(item.key()));
                } else {
                    batchByUrl.computeIfAbsent(item.repoId(), k -> new Batch(item.repoId(), item.repoUrl()))
                            .keys().add(item.key());
                }
            }

            result.addAll(batchByUrl.values());

            return result;
        }

        private static Collection<Batch> removeDuplicateUrls(Collection<Batch> batches) {
            List<Batch> result = new ArrayList<>();
            Set<String> repoUrls = new HashSet<>();
            for (Batch b : batches) {
                if (b.repoUrl() == null) {
                    result.add(b);
                } else if (!repoUrls.contains(b.repoUrl())){
                    result.add(b);
                    repoUrls.add(b.repoUrl());
                }
            }
            return result;
        }

        private void toPreparing(DSLContext tx, List<UUID> processes) {
            tx.update(PROCESS_QUEUE)
                    .set(PROCESS_QUEUE.CURRENT_STATUS, value(ProcessStatus.PREPARING.name()))
                    .where(PROCESS_QUEUE.INSTANCE_ID.in(processes))
                    .execute();
        }
    }
}
