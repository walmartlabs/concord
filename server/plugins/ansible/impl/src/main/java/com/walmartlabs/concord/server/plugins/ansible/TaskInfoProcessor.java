package com.walmartlabs.concord.server.plugins.ansible;

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

import com.walmartlabs.concord.common.StringUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.plugins.ansible.jooq.tables.AnsibleTaskStats;
import org.immutables.value.Value;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.plugins.ansible.jooq.Tables.ANSIBLE_TASK_STATS;
import static org.jooq.impl.DSL.*;

@Named
@Singleton
public class TaskInfoProcessor implements EventProcessor {

    private final Dao dao;

    @Inject
    public TaskInfoProcessor(Dao dao) {
        this.dao = dao;
    }

    @Override
    public void process(DSLContext tx, List<Event> events) {
        Map<TaskInfoKey, TaskDetails> stats = new HashMap<>();

        processTasksFromPlays(events, stats);

        for (Event e : events) {
            AnsibleEvent a = AnsibleEvent.from(e);
            if (a == null) {
                continue;
            }

            if (ignore(a)) {
                continue;
            }

            TaskInfoKey key = ImmutableTaskInfoKey.builder()
                    .instanceId(e.instanceId())
                    .instanceCreatedAt(e.instanceCreatedAt())
                    .playbookId(a.playbookId())
                    .playId(a.getPlayId())
                    .taskId(a.getTaskId())
                    .build();

            String taskName = Optional.ofNullable(a.getTaskName())
                    .map(name -> StringUtils.abbreviate(name, ANSIBLE_TASK_STATS.TASK_NAME.getDataType().length()))
                    .orElse("[task name not found]");
            String status = a.getStatus();
            long taskOrder = a.isSetupTask() ? -1 : e.eventSeq();

            stats.compute(key, (k, v) -> (v == null) ? TaskDetails.builder()
                    .taskName(taskName)
                    .stats(new TaskStats().inc(status))
                    .order(taskOrder)
                    .type(getTaskType(a))
                    .build()
                    : v.inc(status));
        }

        List<TaskInfoItem> items = stats.entrySet().stream()
                .map(v -> ImmutableTaskInfoItem.of(v.getKey(), v.getValue()))
                .collect(Collectors.toList());

        dao.insert(tx, items);
    }

    private void processTasksFromPlays(List<Event> events, Map<TaskInfoKey, TaskDetails> stats) {
        for (Event e : events) {
            PlaybookInfoEvent p = PlaybookInfoEvent.from(e);
            if (p == null) {
                continue;
            }

            for (PlaybookInfoEvent.Play play : p.getPlays()) {
                List<Map<String, Object>> tasks = play.getTasks();
                for (int i = 0; i < tasks.size(); i++) {
                    Map<String, Object> t = tasks.get(i);
                    TaskInfoKey key = ImmutableTaskInfoKey.builder()
                            .instanceId(e.instanceId())
                            .instanceCreatedAt(e.instanceCreatedAt())
                            .playbookId(p.playbookId())
                            .playId(play.getId())
                            .taskId(MapUtils.assertUUID(t, "id"))
                            .build();

                    stats.put(key, TaskDetails.builder()
                            .taskName(MapUtils.assertString(t, "task"))
                            .stats(new TaskStats())
                            .order(i)
                            .overwriteOrder(true)
                            .type("TASK")
                            .build());
                }
            }
        }
    }

    private static String getTaskType(AnsibleEvent e) {
        if (e.isSetupTask()) {
            return "SETUP";
        } else if (e.isHandler()) {
            return "HANDLER";
        } else {
            return "TASK";
        }
    }

    private static boolean ignore(AnsibleEvent e) {
        UUID taskId = e.getTaskId();
        if (taskId == null) {
            return true;
        }
        String status = e.getStatus();
        if (status == null) {
            return true;
        }
        return false;
    }

    @Named
    public static class Dao extends AbstractDao {

        @Inject
        public Dao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public void insert(DSLContext tx, List<TaskInfoItem> items) {
            if (items.isEmpty()) {
                return;
            }

            DbUtils.upsert(tx, items, Dao::update, Dao::insert);
        }

        private static int[] update(DSLContext tx, Connection conn, List<TaskInfoItem> items) throws SQLException {
            AnsibleTaskStats a = ANSIBLE_TASK_STATS;
            String update = tx.update(a)
                    .set(a.OK_COUNT, a.OK_COUNT.plus(value((Long) null)))
                    .set(a.FAILED_COUNT, a.FAILED_COUNT.plus(value((Long) null)))
                    .set(a.UNREACHABLE_COUNT, a.UNREACHABLE_COUNT.plus(value((Long) null)))
                    .set(a.SKIPPED_COUNT, a.SKIPPED_COUNT.plus(value((Long) null)))
                    .set(a.RUNNING_COUNT, a.RUNNING_COUNT.plus(value((Long) null)))
                    .set(a.TASK_ORDER, when(value((Boolean) null).eq(inline(true)), value((Long) null)).otherwise(a.TASK_ORDER))
                    .where(a.INSTANCE_ID.eq(value((UUID) null))
                            .and(a.INSTANCE_CREATED_AT.eq(value((OffsetDateTime) null))
                                    .and(a.PLAY_ID.eq(value((UUID) null))
                                            .and(a.TASK_ID.eq(value((UUID) null))))))
                    .getSQL();

            try (PreparedStatement ps = conn.prepareStatement(update)) {
                for (TaskInfoItem i : items) {
                    ps.setInt(1, i.details().stats().okCount);
                    ps.setInt(2, i.details().stats().failedCount);
                    ps.setInt(3, i.details().stats().unreachableCount);
                    ps.setInt(4, i.details().stats().skippedCount);
                    ps.setInt(5, i.details().stats().runningCount);
                    ps.setBoolean(6, i.details().overwriteOrder());
                    ps.setLong(7, i.details().order());

                    ps.setObject(8, i.key().instanceId());
                    ps.setObject(9, i.key().instanceCreatedAt());
                    ps.setObject(10, i.key().playId());
                    ps.setObject(11, i.key().taskId());

                    ps.addBatch();
                }
                return ps.executeBatch();
            }
        }

        private static void insert(DSLContext tx, Connection conn, List<TaskInfoItem> items) throws SQLException {
            AnsibleTaskStats a = ANSIBLE_TASK_STATS;

            String insert = tx.insertInto(a)
                    .columns(a.INSTANCE_ID,
                            a.INSTANCE_CREATED_AT,
                            a.PLAYBOOK_ID,
                            a.PLAY_ID,
                            a.TASK_ID,
                            a.TASK_NAME,
                            a.TASK_ORDER,
                            a.OK_COUNT,
                            a.FAILED_COUNT,
                            a.UNREACHABLE_COUNT,
                            a.SKIPPED_COUNT,
                            a.RUNNING_COUNT,
                            a.TASK_TYPE)
                    .values(value((UUID) null), null, null, null, null, null, null, null, null, null, null, null, null)
                    .getSQL();

            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                for (TaskInfoItem i : items) {
                    ps.setObject(1, i.key().instanceId());
                    ps.setObject(2, i.key().instanceCreatedAt());
                    ps.setObject(3, i.key().playbookId());
                    ps.setObject(4, i.key().playId());
                    ps.setObject(5, i.key().taskId());
                    ps.setString(6, StringUtils.abbreviate(i.details().taskName(), a.TASK_NAME.getDataType().length()));
                    ps.setLong(7, i.details().order());
                    ps.setInt(8, i.details().stats().okCount);
                    ps.setLong(9, i.details().stats().failedCount);
                    ps.setLong(10, i.details().stats().unreachableCount);
                    ps.setLong(11, i.details().stats().skippedCount);
                    ps.setLong(12, i.details().stats().runningCount);
                    ps.setString(13, i.details().type());

                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface TaskInfoKey {

        UUID instanceId();

        OffsetDateTime instanceCreatedAt();

        UUID playbookId();

        UUID playId();

        UUID taskId();
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface TaskInfoItem {

        @Value.Parameter
        TaskInfoKey key();

        @Value.Parameter
        TaskDetails details();
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface TaskDetails {

        String taskName();

        TaskStats stats();

        String type();

        long order();

        @Value.Default
        default boolean overwriteOrder() {
            return false;
        }

        static ImmutableTaskDetails.Builder builder() {
            return ImmutableTaskDetails.builder();
        }

        default TaskDetails inc(String status) {
            return TaskDetails.builder().from(this)
                    .stats(stats().inc(status))
                    .build();
        }
    }

    static class TaskStats {

        private int okCount;
        private int failedCount;
        private int unreachableCount;
        private int skippedCount;
        private int runningCount;

        public TaskStats inc(String status) {
            switch (status) {
                case "RUNNING": {
                    runningCount++;
                    return this;
                }
                case "SKIPPED": {
                    skippedCount++;
                    runningCount--;
                    return this;
                }
                case "OK": {
                    okCount++;
                    runningCount--;
                    return this;
                }
                case "CHANGED": {
                    runningCount--;
                    return this;
                }
                case "UNREACHABLE": {
                    unreachableCount++;
                    runningCount--;
                    return this;
                }
                case "FAILED": {
                    failedCount++;
                    runningCount--;
                    return this;
                }
            }
            return this;
        }
    }
}
