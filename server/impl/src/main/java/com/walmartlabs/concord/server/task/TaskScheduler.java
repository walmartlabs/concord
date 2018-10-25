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
import org.jooq.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.server.jooq.Tables.TASKS;
import static org.jooq.impl.DSL.currentTimestamp;
import static org.jooq.impl.DSL.value;

@Named
@Singleton
public class TaskScheduler extends PeriodicTask {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);

    private final ExecutorService executor;
    private final SchedulerDao dao;
    private final Map<String, ScheduledTask> tasks;

    @Inject
    public TaskScheduler(Map<String, ScheduledTask> tasks, SchedulerDao dao) {
        super(TimeUnit.SECONDS.toMillis(1), TimeUnit.MINUTES.toMillis(5));

        this.executor = Executors.newCachedThreadPool();
        this.dao = dao;
        this.tasks = tasks;

        this.dao.updateTaskIntervals(tasks);
    }

    @Override
    protected void performTask() {
        String id = dao.poll();
        if (id == null) {
            return;
        }

        ScheduledTask task = tasks.get(id);
        if (task == null) {
            log.error("performTask -> task with id '{}' not found", id);
            return;
        }

        executor.submit(() -> {
            try {
                task.performTask();

                dao.success(id);

                log.info("performTask ['{}'] -> done", id);
            } catch (Exception e) {
                log.error("performTask ['{}'] -> error", id, e);

                dao.fail(id);
            }
        });
    }

    @Named
    private static final class SchedulerDao extends AbstractDao {

        @Inject
        public SchedulerDao(@Named("app") Configuration cfg) {
            super(cfg);
        }

        public String poll() {
            @SuppressWarnings("unchecked")
            Field<? extends Number> i = (Field<? extends Number>) PgUtils.interval("1 second");

            return txResult(tx -> {
                String id = tx.select(TASKS.TASK_ID)
                        .from(TASKS)
                        .where(TASKS.TASK_INTERVAL.greaterThan(0L).and(TASKS.STARTED_AT.isNull()
                                .or(TASKS.FINISHED_AT.isNotNull()
                                        .and(TASKS.FINISHED_AT.plus(TASKS.TASK_INTERVAL.mul(i)).lessOrEqual(currentTimestamp())))))
                        .limit(1)
                        .forUpdate()
                        .skipLocked()
                        .fetchOne(TASKS.TASK_ID);

                if (id == null) {
                    return null;
                }

                tx.update(TASKS)
                        .set(TASKS.STARTED_AT, currentTimestamp())
                        .set(TASKS.TASK_STATUS, value("RUNNING"))
                        .where(TASKS.TASK_ID.eq(id))
                        .execute();

                return id;
            });
        }

        public void success(String taskId) {
            taskFinished(taskId, "OK");
        }

        public void fail(String taskId) {
            taskFinished(taskId, "ERROR");
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

        private void taskFinished(String taskId, String status) {
            tx(tx -> {
                tx.update(TASKS)
                        .set(TASKS.FINISHED_AT, currentTimestamp())
                        .set(TASKS.TASK_STATUS, value(status))
                        .where(TASKS.TASK_ID.eq(taskId))
                        .execute();
            });
        }
    }
}
