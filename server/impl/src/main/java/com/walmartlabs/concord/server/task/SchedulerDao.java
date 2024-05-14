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
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.server.jooq.enums.TaskStatusType;
import com.walmartlabs.concord.server.metrics.FailedTaskError;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import org.jooq.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.Tables.TASKS;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.value;

public final class SchedulerDao extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(SchedulerDao.class);

    @Inject
    public SchedulerDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    public List<String> poll() {
        @SuppressWarnings("unchecked")
        Field<? extends Number> i = (Field<? extends Number>) PgUtils.interval("1 second");

        return txResult(tx -> {
            List<String> ids = tx.select(TASKS.TASK_ID)
                    .from(TASKS)
                    .where(TASKS.TASK_INTERVAL.greaterThan(0L)
                            .and(TASKS.TASK_STATUS.notEqual(TaskStatusType.RUNNING).or(TASKS.TASK_STATUS.isNull()))
                            .and(TASKS.FINISHED_AT.isNull()
                                    .or(TASKS.FINISHED_AT.plus(i.mul(TASKS.TASK_INTERVAL)).lessOrEqual(currentOffsetDateTime()))))
                    .forUpdate()
                    .skipLocked()
                    .fetch(TASKS.TASK_ID);

            if (ids.isEmpty()) {
                return ids;
            }

            tx.update(TASKS)
                    .set(TASKS.STARTED_AT, currentOffsetDateTime())
                    .set(TASKS.TASK_STATUS, value(TaskStatusType.RUNNING))
                    .set(TASKS.FINISHED_AT, (OffsetDateTime) null)
                    .set(TASKS.LAST_UPDATED_AT, currentOffsetDateTime())
                    .where(TASKS.TASK_ID.in(ids))
                    .execute();

            return ids;
        });
    }

    public List<String> pollStalled(DSLContext tx, Field<OffsetDateTime> cutOff) {
        return tx.select(TASKS.TASK_ID)
                .from(TASKS)
                .where(TASKS.LAST_UPDATED_AT.lessThan(cutOff)
                        .and(TASKS.TASK_STATUS.eq(TaskStatusType.RUNNING))
                        .and(TASKS.TASK_INTERVAL.greaterThan(0L)))
                .forUpdate()
                .skipLocked()
                .fetch(TASKS.TASK_ID);
    }

    public void success(String taskId) {
        tx(tx -> taskFinished(tx, taskId, TaskStatusType.OK, null));
    }

    public void error(String taskId, Exception e) {
        tx(tx -> taskFinished(tx, taskId, TaskStatusType.ERROR, e));
    }

    public void stalled(DSLContext tx, String taskId) {
        taskFinished(tx, taskId, TaskStatusType.STALLED, null);
    }

    public void updateTaskIntervals(Collection<ScheduledTask> tasks) {
        tx(tx -> {
            for (ScheduledTask task : tasks) {
                String taskId = task.getId();

                if (task.getIntervalInSec() <= 0) {
                    log.warn("{} has period <= 0, the task will be disabled", taskId);
                }

                tx.insertInto(TASKS, TASKS.TASK_ID, TASKS.TASK_INTERVAL)
                        .values(taskId, task.getIntervalInSec())
                        .onDuplicateKeyUpdate().set(TASKS.TASK_INTERVAL, task.getIntervalInSec())
                        .execute();
            }
        });
    }

    public void updateRunning(Set<String> runningTasks) {
        tx(tx -> tx.update(TASKS)
                .set(TASKS.LAST_UPDATED_AT, currentOffsetDateTime())
                .where(TASKS.TASK_ID.in(runningTasks))
                .execute());
    }

    public void transaction(Tx t) {
        tx(t);
    }

    private void taskFinished(DSLContext tx, String taskId, TaskStatusType status, Exception e) {
        tx.update(TASKS)
                .set(TASKS.FINISHED_AT, currentOffsetDateTime())
                .set(TASKS.LAST_UPDATED_AT, currentOffsetDateTime())
                .set(TASKS.TASK_STATUS, value(status))
                .set(TASKS.LAST_ERROR_AT, e != null ? currentOffsetDateTime() : null)
                .set(TASKS.LAST_ERROR, e != null ? value(toString(e)) : TASKS.LAST_ERROR)
                .where(TASKS.TASK_ID.eq(taskId))
                .execute();
    }

    public List<FailedTaskError> pollErrored() {
        return txResult(tx -> {
            Result<Record3<String, String, OffsetDateTime>> result = tx.select(TASKS.TASK_ID, TASKS.LAST_ERROR, TASKS.LAST_ERROR_AT)
                    .from(TASKS)
                    .where(TASKS.LAST_ERROR_AT.isNotNull()
                            .and(TASKS.LAST_ERROR.isNotNull()))
                    .fetch();
            return result.stream()
                    .map(x -> new FailedTaskError(x.component1(), x.component2(), x.component3()))
                    .collect(Collectors.toList());
        });
    }

    private static String toString(Exception exception) {
        try (StringWriter sw = new StringWriter();
             PrintWriter pw = new PrintWriter(sw)) {
            exception.printStackTrace(pw);
            return sw.toString();
        } catch (IOException e) {
            log.error("toString [{}]-> error: {}", exception, e.getMessage());
            return null;
        }
    }
}
