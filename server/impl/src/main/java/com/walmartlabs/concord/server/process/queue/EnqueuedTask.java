package com.walmartlabs.concord.server.process.queue;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.codahale.metrics.MetricRegistry;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.PeriodicTask;
import com.walmartlabs.concord.server.cfg.EnqueueWorkersConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadBuilder;
import com.walmartlabs.concord.server.process.pipelines.EnqueueProcessPipeline;
import com.walmartlabs.concord.server.process.pipelines.processors.Pipeline;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.value;

public class EnqueuedTask extends PeriodicTask {

    private static final Logger log = LoggerFactory.getLogger(EnqueuedTask.class);

    private static final long ERROR_RETRY_INTERVAL = TimeUnit.SECONDS.toMillis(5);

    private final EnqueueWorkersConfiguration cfg;

    private final Dao dao;

    private final ExecutorService executor;
    private final BlockingQueue<ProcessKey> queue;

    private final AtomicInteger freeWorkersCount;

    @Inject
    public EnqueuedTask(EnqueueWorkersConfiguration cfg, Dao dao, EnqueueProcessPipeline pipeline, MetricRegistry metricRegistry) {
        super(cfg.getInterval().toMillis(), ERROR_RETRY_INTERVAL);

        this.cfg = cfg;
        this.dao = dao;
        this.queue = new ArrayBlockingQueue<>(cfg.getWorkersCount());
        this.freeWorkersCount = new AtomicInteger(cfg.getWorkersCount());

        executor = Executors.newFixedThreadPool(cfg.getWorkersCount());
        for (int i = 0; i < cfg.getWorkersCount(); i++) {
            executor.submit(new Worker(pipeline, queue));
        }

        metricRegistry.gauge("enqueued-workers-available", () -> freeWorkersCount::get);
    }

    @Override
    public void stop() {
        super.stop();

        executor.shutdownNow();

        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    @WithTimer
    protected boolean performTask() {
        if (freeWorkersCount.get() == 0) {
            return false;
        }

        int limit = Math.min(freeWorkersCount.get(), cfg.getWorkersCount());
        List<ProcessKey> keys = dao.poll(limit);
        log.debug("performTask ['{}'] -> size: {}", limit, keys.size());
        if (keys.isEmpty()) {
            return false;
        }

        for (int i = 0; i < keys.size(); i++) {
            freeWorkersCount.decrementAndGet();
        }

        queue.addAll(keys);

        return keys.size() >= limit;
    }

    private void onWorkerFree() {
        freeWorkersCount.incrementAndGet();
    }

    private class Worker implements Runnable {

        private final Pipeline pipeline;

        private final BlockingQueue<ProcessKey> queue;

        private Worker(Pipeline pipeline, BlockingQueue<ProcessKey> queue) {
            this.pipeline = pipeline;
            this.queue = queue;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ProcessKey key = queue.take();
                    startProcess(key);
                } catch (InterruptedException e) {
                    log.warn("run -> interrupted");
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("run -> error", e);
                    sleep(ERROR_RETRY_INTERVAL);
                } finally {
                    onWorkerFree();
                }
            }
        }

        private void startProcess(ProcessKey key) {
            try {
                Payload payload = PayloadBuilder.start(key).build();
                pipeline.process(payload);
            } catch (Exception e) {
                log.error("startProcess ['{}'] -> error", key, e);
            }
        }
    }

    static class Dao extends AbstractDao {

        @Inject
        public Dao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public List<ProcessKey> poll(int limit) {
            return txResult(tx -> {
                List<ProcessKey> result = tx.select(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.CREATED_AT)
                        .from(PROCESS_QUEUE)
                        .where(PROCESS_QUEUE.CURRENT_STATUS.eq(ProcessStatus.NEW.name()))
                        .orderBy(PROCESS_QUEUE.LAST_UPDATED_AT)
                        .limit(limit)
                        .forUpdate()
                        .skipLocked()
                        .fetch(r -> new ProcessKey(r.value1(), r.value2()));

                if (result.isEmpty()) {
                    return result;
                }

                tx.update(PROCESS_QUEUE)
                        .set(PROCESS_QUEUE.CURRENT_STATUS, value(ProcessStatus.PREPARING.name()))
                        .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentOffsetDateTime())
                        .where(PROCESS_QUEUE.INSTANCE_ID.in(result.stream().map(PartialProcessKey::getInstanceId).collect(Collectors.toList())))
                        .execute();

                return result;
            });
        }
    }
}
