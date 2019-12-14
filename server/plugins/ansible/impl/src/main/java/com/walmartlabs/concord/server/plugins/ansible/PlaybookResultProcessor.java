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
import com.walmartlabs.concord.server.plugins.ansible.jooq.tables.AnsiblePlaybookResult;
import org.immutables.value.Value;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.plugins.ansible.jooq.Tables.ANSIBLE_PLAYBOOK_RESULT;
import static org.jooq.impl.DSL.value;

@Named
@Singleton
public class PlaybookResultProcessor implements EventProcessor {

    private final Dao dao;

    @Inject
    public PlaybookResultProcessor(Dao dao) {
        this.dao = dao;
    }

    @Override
    public void process(DSLContext tx, List<Event> events) {
        List<PlaybookResult> results = new ArrayList<>();
        for (Event e : events) {
            PlaybookResultEvent p = PlaybookResultEvent.from(e);
            if (p == null) {
                continue;
            }

            results.add(ImmutablePlaybookResult.builder()
                    .instanceId(e.instanceId())
                    .instanceCreatedAt(e.instanceCreatedAt())
                    .playbookId(p.playbookId())
                    .status(p.getStatus())
                    .build());
        }

        dao.insert(tx, results);
    }

    @Named
    public static class Dao extends AbstractDao {

        @Inject
        public Dao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public void insert(DSLContext tx, List<PlaybookResult> items) {
            if (items.isEmpty()) {
                return;
            }

            AnsiblePlaybookResult a = ANSIBLE_PLAYBOOK_RESULT.as("a");

            SelectConditionStep<Record1<Integer>> exists = tx.selectOne()
                    .from(a)
                    .where(a.INSTANCE_ID.eq(value((UUID)null))
                            .and(a.INSTANCE_CREATED_AT.eq(value((Timestamp)null))
                                    .and(a.PLAYBOOK_ID.eq(value((UUID)null)))));

            SelectConditionStep<Record4<UUID, Timestamp, UUID, String>> values =
                    tx.select(value((UUID) null),
                            value((Timestamp)null),
                            value((UUID) null),
                            value((String) null))
                            .whereNotExists(exists);

            String insert = tx.insertInto(a)
                    .columns(a.INSTANCE_ID,
                            a.INSTANCE_CREATED_AT,
                            a.PLAYBOOK_ID,
                            a.STATUS)
                    .select(values)
                    .getSQL();

            tx.connection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    for (PlaybookResult p : items) {
                        ps.setObject(1, p.instanceId());
                        ps.setTimestamp(2, p.instanceCreatedAt());
                        ps.setObject(3, p.playbookId());
                        ps.setString(4, p.status());

                        ps.setObject(5, p.instanceId());
                        ps.setTimestamp(6, p.instanceCreatedAt());
                        ps.setObject(7, p.playbookId());

                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            });
        }
    }

    @Value.Immutable
    interface PlaybookResult {

        UUID instanceId();

        Timestamp instanceCreatedAt();

        UUID playbookId();

        String status();
    }
}
