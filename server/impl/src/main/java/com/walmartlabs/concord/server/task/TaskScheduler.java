package com.walmartlabs.concord.server.task;

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

import com.walmartlabs.concord.server.PeriodicTask;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import org.jooq.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.db.PgUtils.interval;
import static java.util.stream.Collectors.toMap;
import static org.jooq.impl.DSL.currentOffsetDateTime;

public class TaskScheduler extends PeriodicTask {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);

    private static final long POLL_INTERVAL = TimeUnit.SECONDS.toMillis(1);
    private static final long ERROR_DELAY = TimeUnit.SECONDS.toMillis(10);
    private static final String MAX_STALLED_AGE = "1 minute";
    private static final long STALLED_CHECK_INTERVAL = TimeUnit.SECONDS.toMillis(20);
    private static final long RUNNING_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(20);

    private final ExecutorService executor;
    private final SchedulerDao dao;
    private final Map<String, ScheduledTask> tasks;
    private final Set<String> runningTasks = Collections.synchronizedSet(new HashSet<>());

    private long lastUpdateDate;
    private long lasStalledCheckDate;

    @Inject
    public TaskScheduler(Set<ScheduledTask> tasks, SchedulerDao dao) {
        super(POLL_INTERVAL, ERROR_DELAY);

        this.executor = Executors.newCachedThreadPool();
        this.dao = dao;
        this.tasks = tasks.stream().collect(toMap(ScheduledTask::getId, t -> t));

        this.dao.updateTaskIntervals(tasks);
    }

    @Override
    protected boolean performTask() {
        if (lastUpdateDate + RUNNING_UPDATE_INTERVAL <= System.currentTimeMillis()) {
            updateRunningTasks();
            lastUpdateDate = System.currentTimeMillis();
        }

        if (lasStalledCheckDate + STALLED_CHECK_INTERVAL <= System.currentTimeMillis()) {
            failStalled();
            lasStalledCheckDate = System.currentTimeMillis();
        }

        List<String> ids = dao.poll();
        ids.forEach(this::startTask);

        return false;
    }

    @Override
    public void stop() {
        super.stop();

        executor.shutdown();

        try {
            if (executor.awaitTermination(5, TimeUnit.MINUTES)) {
                log.info("stop -> done");
            } else {
                log.info("stop -> timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void startTask(String id) {
        ScheduledTask task = tasks.get(id);
        if (task == null) {
            log.error("startTask -> task with id '{}' not found", id);
            return;
        }

        executor.submit(() -> {
            try {
                runningTasks.add(id);

                task.performTask();

                dao.success(id);

                log.debug("startTask ['{}'] -> done", id);
            } catch (Exception e) {
                log.error("startTask ['{}'] -> error", id, e);

                dao.error(id, e);
            } finally {
                runningTasks.remove(id);
            }
        });
    }

    private void updateRunningTasks() {
        Set<String> forUpdate;
        synchronized (runningTasks) {
            forUpdate = new HashSet<>(runningTasks);
        }

        if (forUpdate.isEmpty()) {
            return;
        }

        try {
            dao.updateRunning(forUpdate);
        } catch (Exception e) {
            log.error("updateRunningTasks -> error: {}", e.getMessage());
        }
    }

    private void failStalled() {
        Field<OffsetDateTime> cutOff = currentOffsetDateTime().minus(interval(MAX_STALLED_AGE));

        try {
            dao.transaction(tx -> {
                List<String> ids = dao.pollStalled(tx, cutOff);
                for (String id : ids) {
                    dao.stalled(tx, id);
                    log.info("failStalled -> marked as failed: {}", id);
                }
            });
        } catch (Exception e) {
            log.error("failStalled -> error: {}", e.getMessage());
        }
    }

}
