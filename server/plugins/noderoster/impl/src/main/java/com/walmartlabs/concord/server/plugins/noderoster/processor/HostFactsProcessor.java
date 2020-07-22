package com.walmartlabs.concord.server.plugins.noderoster.processor;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.plugins.noderoster.HostManager;
import com.walmartlabs.concord.server.plugins.noderoster.db.NodeRosterDB;
import com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHostFacts;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.immutables.value.Value;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHostFacts.NODE_ROSTER_HOST_FACTS;
import static org.jooq.impl.DSL.value;

/**
 * Collects facts received from "gather_facts" steps and saves them in the DB.
 */
@Named
public class HostFactsProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(HostFactsProcessor.class);

    private final Dao dao;
    private final HostManager hosts;

    @Inject
    public HostFactsProcessor(Dao dao, HostManager hosts) {
        this.dao = dao;
        this.hosts = hosts;
    }

    @Override
    @WithTimer
    public void process(List<AnsibleEvent> events) {
        List<HostFactsItem> items = new ArrayList<>();

        for (AnsibleEvent e : events) {
            String host = e.data().getHost();
            Map<String, Object> facts = getFacts(e.data());
            if (host != null && facts != null) {
                items.add(HostFactsItem.builder()
                        .instanceId(e.instanceId())
                        .instanceCreatedAt(e.instanceCreatedAt())
                        .host(hosts.getOrCreate(host))
                        .facts(facts)
                        .build());
            }
        }

        if (!items.isEmpty()) {
            dao.insert(items);
        }

        log.info("process -> events: {}, items: {}", events.size(), items.size());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getFacts(EventData eventData) {
        if (!eventData.isPostEvent()) {
            return null;
        }

        String action = eventData.getAction();
        String task = eventData.getTask();
        if ("setup".equals(action) || "Gathering Facts".equals(task)) {
            Map<String, Object> result = eventData.getMap("result");
            if (result == null) {
                return null;
            }

            return (Map<String, Object>) result.get("ansible_facts");
        }
        return null;
    }

    @Named
    public static class Dao extends AbstractDao {

        private final ObjectMapper objectMapper;

        @Inject
        public Dao(@NodeRosterDB Configuration cfg) {
            super(cfg);
            this.objectMapper = new ObjectMapper();
        }

        @WithTimer
        public void insert(List<HostFactsItem> items) {
            tx(tx -> insert(tx, items));
        }

        private void insert(DSLContext tx, List<HostFactsItem> items) {
            tx.connection(conn -> {
                int[] updated = update(tx, conn, items);

                List<HostFactsItem> itemsForInsert = new ArrayList<>();
                for (int i = 0; i < updated.length; i++) {
                    if (updated[i] < 1) {
                        itemsForInsert.add(items.get(i));
                    }
                }

                if (!itemsForInsert.isEmpty()) {
                    insert(tx, conn, itemsForInsert);
                }

                log.info("insert -> updated: {}, inserted: {}",
                        items.size() - itemsForInsert.size(), itemsForInsert.size());
            });
        }

        @WithTimer
        protected int[] update(DSLContext tx, Connection conn, List<HostFactsItem> items) throws SQLException {
            NodeRosterHostFacts f = NODE_ROSTER_HOST_FACTS.as("f");

            String update = tx.update(f)
                    .set(f.FACTS, (JSONB) null)
                    .where(f.INSTANCE_ID.eq(value((UUID) null))
                            .and(f.INSTANCE_CREATED_AT.eq(value((OffsetDateTime) null))
                                    .and(f.HOST_ID.eq(value((UUID) null)))))
                    .getSQL();

            try (PreparedStatement ps = conn.prepareStatement(update)) {
                for (HostFactsItem i : items) {
                    ps.setString(1, serialize(i.facts()));
                    ps.setObject(2, i.instanceId());
                    ps.setObject(3, i.instanceCreatedAt());
                    ps.setObject(4, i.host());

                    ps.addBatch();
                }
                return ps.executeBatch();
            }
        }

        @WithTimer
        protected void insert(DSLContext tx, Connection conn, List<HostFactsItem> items) throws SQLException {
            NodeRosterHostFacts f = NODE_ROSTER_HOST_FACTS.as("f");

            String insert = tx.insertInto(f)
                    .columns(f.HOST_ID,
                            f.INSTANCE_ID,
                            f.INSTANCE_CREATED_AT,
                            f.FACTS)
                    .values(value((UUID) null), null, null, null)
                    .getSQL();

            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                for (HostFactsItem i : items) {
                    ps.setObject(1, i.host());
                    ps.setObject(2, i.instanceId());
                    ps.setObject(3, i.instanceCreatedAt());
                    ps.setString(4, serialize(i.facts()));

                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        private String serialize(Map<String, Object> m) {
            if (m == null) {
                return null;
            }

            try {
                return objectMapper.writeValueAsString(m);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Value.Immutable
    interface HostFactsItem {

        UUID host();

        UUID instanceId();

        OffsetDateTime instanceCreatedAt();

        Map<String, Object> facts();

        static ImmutableHostFactsItem.Builder builder() {
            return ImmutableHostFactsItem.builder();
        }
    }
}
