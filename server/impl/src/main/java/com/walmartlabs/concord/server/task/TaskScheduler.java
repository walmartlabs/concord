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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.server.PeriodicTask;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.db.PgUtils.interval;
import static com.walmartlabs.concord.server.jooq.Tables.TASKS;
import static org.jooq.impl.DSL.currentTimestamp;
import static org.jooq.impl.DSL.value;

@Named
@Singleton
public class TaskScheduler extends PeriodicTask {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);

    private static final long POLL_INTERVAL = TimeUnit.SECONDS.toMillis(1);
    private static final long ERROR_DELAY = TimeUnit.SECONDS.toMillis(10);
    private static final String MAX_STALLED_AGE = "1 minute";

    private final ExecutorService executor;
    private final SchedulerDao dao;
    private final Map<String, ScheduledTask> tasks;
    private final Set<String> runningTasks = Collections.synchronizedSet(new HashSet<>());

    @Inject
    public TaskScheduler(Map<String, ScheduledTask> tasks, SchedulerDao dao) {
        super(POLL_INTERVAL, ERROR_DELAY);

        this.executor = Executors.newCachedThreadPool();
        this.dao = dao;
        this.tasks = tasks;

        this.dao.updateTaskIntervals(tasks);
    }

    @Override
    protected void performTask() {
        List<String> ids = dao.poll();
        if (ids.isEmpty()) {
            return;
        }

        ids.forEach(this::startTask);

        updateRunningTasks();

        failStalled();
    }

    @Override
    public void stop() {
        super.stop();
        executor.shutdown();
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

                log.info("startTask ['{}'] -> done", id);
            } catch (Exception e) {
                log.error("startTask ['{}'] -> error", id, e);

                dao.fail(id);
            } finally {
                runningTasks.remove(id);
            }
        });
    }

    private void updateRunningTasks() {
        synchronized (runningTasks) {
            dao.updateRunning(runningTasks);
        }
    }

    private void failStalled() {
        Field<Timestamp> cutOff = currentTimestamp().minus(interval(MAX_STALLED_AGE));

        dao.transaction(tx -> {
            List<String> ids = dao.pollStalled(tx, cutOff);
            for(String id : ids) {
                dao.fail(tx, id);
                log.info("failStalled -> marked as failed: {}", id);
            }
        });
    }

    @Named
    private static final class SchedulerDao extends AbstractDao {

        @Inject
        public SchedulerDao(@Named("app") Configuration cfg) {
            super(cfg);
        }

        public List<String> poll() {
            @SuppressWarnings("unchecked")
            Field<? extends Number> i = (Field<? extends Number>) PgUtils.interval("1 second");

            return txResult(tx -> {
                List<String> ids = tx.select(TASKS.TASK_ID)
                        .from(TASKS)
                        .where(TASKS.TASK_INTERVAL.greaterThan(0L).and(TASKS.STARTED_AT.isNull()
                                .or(TASKS.FINISHED_AT.isNotNull()
                                        .and(TASKS.FINISHED_AT.plus(TASKS.TASK_INTERVAL.mul(i)).lessOrEqual(currentTimestamp())))))
                        .forUpdate()
                        .skipLocked()
                        .fetch(TASKS.TASK_ID);

                if (ids.isEmpty()) {
                    return ids;
                }

                tx.update(TASKS)
                        .set(TASKS.STARTED_AT, currentTimestamp())
                        .set(TASKS.TASK_STATUS, value("RUNNING"))
                        .set(TASKS.FINISHED_AT, (Timestamp)null)
                        .set(TASKS.LAST_UPDATED_AT, currentTimestamp())
                        .where(TASKS.TASK_ID.in(ids))
                        .execute();

                return ids;
            });
        }

        public List<String> pollStalled(DSLContext tx, Field<Timestamp> cutOff) {
            return tx.select(TASKS.TASK_ID)
                    .from(TASKS)
                    .where(TASKS.LAST_UPDATED_AT.lessThan(cutOff)
                            .and(TASKS.FINISHED_AT.isNull())
                            .and(TASKS.TASK_INTERVAL.greaterThan(0L)))
                    .forUpdate()
                    .skipLocked()
                    .fetch(TASKS.TASK_ID);
        }

        public void success(String taskId) {
            tx(tx -> taskFinished(tx, taskId, "OK"));
        }

        public void fail(String taskId) {
            tx(tx -> fail(tx, taskId));
        }

        public void fail(DSLContext tx, String taskId) {
            taskFinished(tx, taskId, "ERROR");
        }

        public void updateTaskIntervals(Map<String, ScheduledTask> tasks) {
            tx(tx -> {
                for (Map.Entry<String, ScheduledTask> e : tasks.entrySet()) {
                    tx.insertInto(TASKS, TASKS.TASK_ID, TASKS.TASK_INTERVAL)
                            .values(e.getKey(), e.getValue().getIntervalInSec())
                            .onDuplicateKeyUpdate().set(TASKS.TASK_INTERVAL, e.getValue().getIntervalInSec())
                            .execute();
                }
            });
        }

        public void updateRunning(Set<String> runningTasks) {
            tx(tx -> {
                tx.update(TASKS)
                        .set(TASKS.LAST_UPDATED_AT, currentTimestamp())
                        .where(TASKS.TASK_ID.in(runningTasks))
                        .execute();
            });
        }

        private void transaction(Tx t) {
            tx(t);
        }

        private void taskFinished(DSLContext tx, String taskId, String status) {
            tx.update(TASKS)
                    .set(TASKS.FINISHED_AT, currentTimestamp())
                    .set(TASKS.LAST_UPDATED_AT, currentTimestamp())
                    .set(TASKS.TASK_STATUS, value(status))
                    .where(TASKS.TASK_ID.eq(taskId))
                    .execute();
        }
    }
}
