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
import com.walmartlabs.concord.server.plugins.ansible.jooq.tables.AnsiblePlayStats;
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

import static com.walmartlabs.concord.server.plugins.ansible.jooq.Tables.ANSIBLE_PLAY_STATS;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.value;

@Named
@Singleton
public class PlayInfoProcessor implements EventProcessor {

    private static final Set<String> FINISHED_TASK_STATUSES = new HashSet<>(Arrays.asList("SKIPPED", "OK", "UNREACHABLE", "FAILED"));

    private final Dao dao;

    @Inject
    public PlayInfoProcessor(Dao dao) {
        this.dao = dao;
    }

    @Override
    public void process(DSLContext tx, List<Event> events) {
        Map<PlayInfoKey, Long> finishedTasks = collectFinished(events);

        List<PlayInfoItem> plays = new ArrayList<>();
        for (Event e : events) {
            PlaybookInfoEvent p = PlaybookInfoEvent.from(e);
            if (p == null) {
                continue;
            }

            List<PlaybookInfoEvent.Play> eventPlays = p.getPlays();
            for (int i = 0; i < eventPlays.size(); i++) {
                PlaybookInfoEvent.Play play = eventPlays.get(i);

                PlayInfoKey key = PlayInfoKey.builder()
                        .instanceId(e.instanceId())
                        .instanceCreatedAt(e.instanceCreatedAt())
                        .playbookId(p.playbookId())
                        .playId(play.getId())
                        .build();
                Long finishedCount = finishedTasks.remove(key);

                String playName = StringUtils.abbreviate(play.getName(), ANSIBLE_PLAY_STATS.PLAY_NAME.getDataType().length());
                if (playName == null) {
                    // ignore invalid data (i.e. data produced by old ansible-task versions)
                    continue;
                }

                plays.add(ImmutablePlayInfoItem.builder()
                        .key(key)
                        .playName(playName)
                        .playOrder(i)
                        .hostCount(play.getHostCount())
                        .taskCount(play.getTaskCount())
                        .finishedCount(finishedCount != null ? finishedCount : 0)
                        .build());
            }
        }

        dao.insert(tx, plays);
        dao.insertFinished(tx, finishedTasks.entrySet().stream().map(e -> PlayInfoFinishedItem.of(e.getKey(), e.getValue())).collect(Collectors.toList()));
    }

    private static Map<PlayInfoKey, Long> collectFinished(List<Event> events) {
        Map<PlayInfoKey, Long> result = new HashMap<>();
        for (Event e : events) {
            AnsibleEvent event = AnsibleEvent.from(e);
            if (event == null) {
                continue;
            }

            UUID playId = event.getPlayId();
            if (playId == null) {
                // event from old plugin
                continue;
            }

            if (ignore(event)) {
                continue;
            }

            if (isFinished(event)) {
                PlayInfoKey key = PlayInfoKey.builder()
                        .instanceId(e.instanceId())
                        .instanceCreatedAt(e.instanceCreatedAt())
                        .playbookId(event.playbookId())
                        .playId(playId)
                        .build();

                result.compute(key, (k, v) -> (v == null) ? 1 : v + 1);
            }
        }
        return result;
    }

    private static boolean ignore(AnsibleEvent e) {
        String action = e.getAction();
        if ("gather_facts".equals(action)) {
            return true;
        }

        return e.isHandler();
    }

    private static boolean isFinished(AnsibleEvent e) {
        String status = e.getStatus();
        if (status == null) {
            return false;
        }

        return FINISHED_TASK_STATUSES.contains(status);
    }

    @Named
    public static class Dao extends AbstractDao {

        @Inject
        public Dao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public void insertFinished(DSLContext tx, List<PlayInfoFinishedItem> items) {
            if (items.isEmpty()) {
                return;
            }

            DbUtils.upsert(tx, items, Dao::updateFinishedItems, Dao::insertFinishedItems);
        }

        public void insert(DSLContext tx, List<PlayInfoItem> plays) {
            if (plays.isEmpty()) {
                return;
            }

            DbUtils.upsert(tx, plays, Dao::update, Dao::insert);
        }

        private static int[] updateFinishedItems(DSLContext tx, Connection conn, List<PlayInfoFinishedItem> items) throws SQLException {
            AnsiblePlayStats a = ANSIBLE_PLAY_STATS;
            String update = tx.update(a)
                    .set(a.FINISHED_TASK_COUNT, a.FINISHED_TASK_COUNT.plus(value((Long) null)))
                    .where(a.INSTANCE_ID.eq(value((UUID) null))
                            .and(a.INSTANCE_CREATED_AT.eq(value((OffsetDateTime) null))
                                    .and(a.PLAY_ID.eq(value((UUID) null)))))
                    .getSQL();

            try (PreparedStatement ps = conn.prepareStatement(update)) {
                for (PlayInfoFinishedItem p : items) {
                    ps.setLong(1, p.finishedCount());
                    ps.setObject(2, p.key().instanceId());
                    ps.setObject(3, p.key().instanceCreatedAt());
                    ps.setObject(4, p.key().playId());

                    ps.addBatch();
                }
                return ps.executeBatch();
            }
        }

        private static void insertFinishedItems(DSLContext tx, Connection conn, List<PlayInfoFinishedItem> items) throws SQLException {
            String insert = tx.insertInto(ANSIBLE_PLAY_STATS)
                    .columns(ANSIBLE_PLAY_STATS.INSTANCE_ID,
                            ANSIBLE_PLAY_STATS.INSTANCE_CREATED_AT,
                            ANSIBLE_PLAY_STATS.PLAYBOOK_ID,
                            ANSIBLE_PLAY_STATS.PLAY_ID,
                            ANSIBLE_PLAY_STATS.PLAY_ORDER,
                            ANSIBLE_PLAY_STATS.HOST_COUNT,
                            ANSIBLE_PLAY_STATS.TASK_COUNT,
                            ANSIBLE_PLAY_STATS.FINISHED_TASK_COUNT)
                    .values(value((UUID) null), null, null, null, inline(0), inline(0L), inline(0), null)
                    .getSQL();

            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                for (PlayInfoFinishedItem p : items) {
                    ps.setObject(1, p.key().instanceId());
                    ps.setObject(2, p.key().instanceCreatedAt());
                    ps.setObject(3, p.key().playbookId());
                    ps.setObject(4, p.key().playId());
                    ps.setLong(5, p.finishedCount());

                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        private static int[] update(DSLContext tx, Connection conn, List<PlayInfoItem> plays) throws SQLException {
            AnsiblePlayStats a = ANSIBLE_PLAY_STATS;
            String update = tx.update(a)
                    .set(a.PLAY_NAME, value((String) null))
                    .set(a.PLAY_ORDER, value((Integer) null))
                    .set(a.TASK_COUNT, value((Integer) null))
                    .set(a.HOST_COUNT, value((Long) null))
                    .set(a.FINISHED_TASK_COUNT, a.FINISHED_TASK_COUNT.plus(value((Long) null)))
                    .where(a.INSTANCE_ID.eq(value((UUID) null))
                            .and(a.INSTANCE_CREATED_AT.eq(value((OffsetDateTime) null))
                                    .and(a.PLAY_ID.eq(value((UUID) null)))))
                    .getSQL();

            try (PreparedStatement ps = conn.prepareStatement(update)) {
                for (PlayInfoItem p : plays) {
                    ps.setString(1, p.playName());
                    ps.setInt(2, p.playOrder());
                    ps.setInt(3, p.taskCount());
                    ps.setLong(4, p.hostCount());
                    ps.setLong(5, p.finishedCount());
                    ps.setObject(6, p.key().instanceId());
                    ps.setObject(7, p.key().instanceCreatedAt());
                    ps.setObject(8, p.key().playId());

                    ps.addBatch();
                }
                return ps.executeBatch();
            }
        }

        private static void insert(DSLContext tx, Connection conn, List<PlayInfoItem> plays) throws SQLException {
            String insert = tx.insertInto(ANSIBLE_PLAY_STATS)
                    .columns(ANSIBLE_PLAY_STATS.INSTANCE_ID,
                            ANSIBLE_PLAY_STATS.INSTANCE_CREATED_AT,
                            ANSIBLE_PLAY_STATS.PLAYBOOK_ID,
                            ANSIBLE_PLAY_STATS.PLAY_ID,
                            ANSIBLE_PLAY_STATS.PLAY_NAME,
                            ANSIBLE_PLAY_STATS.PLAY_ORDER,
                            ANSIBLE_PLAY_STATS.HOST_COUNT,
                            ANSIBLE_PLAY_STATS.TASK_COUNT,
                            ANSIBLE_PLAY_STATS.FINISHED_TASK_COUNT)
                    .values(value((UUID) null), null, null, null, null, null, null, null, null)
                    .getSQL();

            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                for (PlayInfoItem p : plays) {
                    ps.setObject(1, p.key().instanceId());
                    ps.setObject(2, p.key().instanceCreatedAt());
                    ps.setObject(3, p.key().playbookId());
                    ps.setObject(4, p.key().playId());
                    ps.setString(5, p.playName());
                    ps.setInt(6, p.playOrder());
                    ps.setLong(7, p.hostCount());
                    ps.setInt(8, p.taskCount());
                    ps.setLong(9, p.finishedCount());

                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface PlayInfoKey {

        UUID instanceId();

        OffsetDateTime instanceCreatedAt();

        UUID playbookId();

        UUID playId();

        static ImmutablePlayInfoKey.Builder builder() {
            return ImmutablePlayInfoKey.builder();
        }
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface PlayInfoFinishedItem {

        PlayInfoKey key();

        long finishedCount();

        static PlayInfoFinishedItem of(PlayInfoKey key, long count) {
            return ImmutablePlayInfoFinishedItem.builder()
                    .key(key)
                    .finishedCount(count)
                    .build();
        }
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface PlayInfoItem {

        PlayInfoKey key();

        String playName();

        int playOrder();

        long hostCount();

        int taskCount();

        long finishedCount();
    }
}
