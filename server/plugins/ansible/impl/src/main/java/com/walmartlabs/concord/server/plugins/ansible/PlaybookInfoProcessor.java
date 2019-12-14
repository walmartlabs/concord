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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.plugins.ansible.jooq.tables.AnsiblePlaybookStats;
import org.immutables.value.Value;
import org.jooq.*;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.plugins.ansible.jooq.Tables.ANSIBLE_PLAYBOOK_STATS;
import static org.jooq.impl.DSL.value;

@Named
@Singleton
public class PlaybookInfoProcessor implements EventProcessor {

    private final Dao dao;

    @Inject
    public PlaybookInfoProcessor(Dao dao) {
        this.dao = dao;
    }

    @Override
    public void process(DSLContext tx, List<Event> events) {
        List<PlaybookInfo> playbooks = new ArrayList<>();
        for (Event e : events) {
            PlaybookInfoEvent p = PlaybookInfoEvent.from(e);
            if (p == null) {
                continue;
            }

            playbooks.add(ImmutablePlaybookInfo.builder()
                    .instanceId(e.instanceId())
                    .instanceCreatedAt(e.instanceCreatedAt())
                    .playbookId(p.playbookId())
                    .name(p.getPlaybookName())
                    .startedAt(e.eventDate())
                    .hostCount(p.getHostsCount())
                    .playCount(p.getPlays().size())
                    .totalWork(p.getTotalWork())
                    .currentRetryCount(p.retryCount())
                    .build());
        }

        dao.insert(tx, playbooks);
    }

    @Named
    public static class Dao extends AbstractDao {

        @Inject
        public Dao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public void insert(DSLContext tx, List<PlaybookInfo> items) {
            if (items.isEmpty()) {
                return;
            }

            AnsiblePlaybookStats a = ANSIBLE_PLAYBOOK_STATS.as("a");

            SelectConditionStep<Record1<Integer>> exists = tx.selectOne()
                    .from(a)
                    .where(a.INSTANCE_ID.eq(value((UUID)null))
                            .and(a.INSTANCE_CREATED_AT.eq(value((Timestamp)null))
                                    .and(a.PLAYBOOK_ID.eq(value((UUID)null)))));

            SelectConditionStep<Record9<UUID, Timestamp, UUID, String, Timestamp, Integer, Integer, Integer, Integer>> values =
                    tx.select(value((UUID) null),
                            value((Timestamp)null),
                            value((UUID) null),
                            value((String) null),
                            value((Timestamp)null),
                            value((Integer)null),
                            value((Integer)null),
                            value((Integer)null),
                            value((Integer)null))
                            .whereNotExists(exists);

            String insert = tx.insertInto(a)
                    .columns(a.INSTANCE_ID,
                            a.INSTANCE_CREATED_AT,
                            a.PLAYBOOK_ID,
                            a.NAME,
                            a.STARTED_AT,
                            a.HOST_COUNT,
                            a.PLAY_COUNT,
                            a.TOTAL_WORK,
                            a.RETRY_NUM)
                    .select(values)
                    .getSQL();

            tx.connection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    for (PlaybookInfo p : items) {
                        ps.setObject(1, p.instanceId());
                        ps.setTimestamp(2, p.instanceCreatedAt());
                        ps.setObject(3, p.playbookId());
                        ps.setObject(4, p.name());
                        ps.setTimestamp(5, p.startedAt());
                        ps.setLong(6, p.hostCount());
                        ps.setInt(7, p.playCount());
                        ps.setLong(8, p.totalWork());
                        ps.setObject(9, p.currentRetryCount());

                        ps.setObject(10, p.instanceId());
                        ps.setObject(11, p.instanceCreatedAt());
                        ps.setObject(12, p.playbookId());

                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            });
        }
    }

    @Value.Immutable
    interface PlaybookInfo {

        UUID instanceId();

        Timestamp instanceCreatedAt();

        UUID playbookId();

        String name();

        Timestamp startedAt();

        long hostCount();

        int playCount();

        long totalWork();

        @Nullable
        Integer currentRetryCount();
    }
}
