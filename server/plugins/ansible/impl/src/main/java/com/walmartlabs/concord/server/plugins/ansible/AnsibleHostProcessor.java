package com.walmartlabs.concord.server.plugins.ansible;

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

import com.walmartlabs.concord.common.StringUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import org.immutables.value.Value;
import org.jooq.CaseValueStep;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Field;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

import static com.walmartlabs.concord.server.plugins.ansible.jooq.tables.AnsibleHosts.ANSIBLE_HOSTS;
import static org.jooq.impl.DSL.*;

@Named
@Singleton
public class AnsibleHostProcessor implements EventProcessor {

    private final Dao dao;

    @Inject
    public AnsibleHostProcessor(Dao dao) {
        this.dao = dao;
    }

    @Override
    public void process(DSLContext tx, List<Event> events) {
        List<AnsibleEvent> eventItems = new ArrayList<>();
        for (Event e : events) {
            AnsibleEvent event = AnsibleEvent.from(e);
            if (event == null) {
                continue;
            }

            eventItems.add(event);
        }

        List<HostItem> result = combineEvents(eventItems);
        dao.insert(tx, result);
    }

    private static List<HostItem> combineEvents(List<AnsibleEvent> events) {
        Map<HostItem.Key, HostItem> result = new HashMap<>();
        for (AnsibleEvent e : events) {
            result.compute(HostItem.Key.from(e),
                    (k, v) -> (v == null) ? HostItem.from(e) : combine(v, e));
        }
        return new ArrayList<>(result.values());
    }

    private static HostItem combine(HostItem hostItem, AnsibleEvent newEvent) {
        long duration = hostItem.duration() + newEvent.duration();

        HostStatus status = getHostStatus(newEvent);
        long eventSeq = newEvent.eventSeq();
        if (status.weight() <= hostItem.status().weight()) {
            status = hostItem.status();
            eventSeq = hostItem.eventSeq();
        }

        return ImmutableHostItem.builder()
                .from(hostItem)
                .duration(duration)
                .status(status)
                .eventSeq(eventSeq)
                .build();
    }

    @Named
    public static class Dao extends AbstractDao {

        @Inject
        public Dao(@MainDB Configuration cfg) {
            super(cfg);
        }

        @Override
        public <T> T txResult(TxResult<T> t) {
            return super.txResult(t);
        }

        public void insert(DSLContext tx, List<HostItem> items) {
            DbUtils.upsert(tx, items, Dao::update, Dao::insert);
        }

        private static int[] update(DSLContext tx, Connection conn, List<HostItem> hosts) throws SQLException {
            Field<Integer> currentStatusWeight = decodeStatus(choose(ANSIBLE_HOSTS.STATUS));
            Field<Integer> newStatusWeight = decodeStatus(choose(value((String) null)));

            String update = tx.update(ANSIBLE_HOSTS)
                    .set(ANSIBLE_HOSTS.DURATION, ANSIBLE_HOSTS.DURATION.plus(value((Integer) null)))
                    .set(ANSIBLE_HOSTS.STATUS, when(currentStatusWeight.greaterThan(newStatusWeight), ANSIBLE_HOSTS.STATUS).otherwise(value((String) null)))
                    .set(ANSIBLE_HOSTS.EVENT_SEQ, when(currentStatusWeight.greaterThan(newStatusWeight), ANSIBLE_HOSTS.EVENT_SEQ).otherwise(value((Long) null)))
                    .where(ANSIBLE_HOSTS.INSTANCE_ID.eq(value((UUID) null))
                            .and(ANSIBLE_HOSTS.INSTANCE_CREATED_AT.eq(value((OffsetDateTime) null))
                                    .and(ANSIBLE_HOSTS.HOST.eq(value((String) null))
                                            .and(ANSIBLE_HOSTS.HOST_GROUP.eq(value((String) null))
                                                    .and(ANSIBLE_HOSTS.PLAYBOOK_ID.eq((UUID)null))))))
                    .getSQL();

            try (PreparedStatement ps = conn.prepareStatement(update)) {
                for (HostItem h : hosts) {
                    // set duration
                    ps.setLong(1, h.duration());

                    // set status
                    ps.setString(2, h.status().name());
                    ps.setString(3, h.status().name());

                    // set event seq
                    ps.setString(4, h.status().name());
                    ps.setLong(5, h.eventSeq());

                    ps.setObject(6, h.key().instanceId());
                    ps.setObject(7, h.key().instanceCreatedAt());
                    ps.setString(8, StringUtils.abbreviate(h.key().host(), ANSIBLE_HOSTS.HOST.getDataType().length()));
                    ps.setString(9, StringUtils.abbreviate(h.key().hostGroup(), ANSIBLE_HOSTS.HOST_GROUP.getDataType().length()));
                    ps.setObject(10, h.key().playbookId());

                    ps.addBatch();
                }
                return ps.executeBatch();
            }
        }

        private static void insert(DSLContext tx, Connection conn, List<HostItem> hosts) throws SQLException {
            String insert = tx.insertInto(ANSIBLE_HOSTS)
                    .columns(ANSIBLE_HOSTS.INSTANCE_ID,
                            ANSIBLE_HOSTS.INSTANCE_CREATED_AT,
                            ANSIBLE_HOSTS.PLAYBOOK_ID,
                            ANSIBLE_HOSTS.HOST,
                            ANSIBLE_HOSTS.HOST_GROUP,
                            ANSIBLE_HOSTS.STATUS,
                            ANSIBLE_HOSTS.DURATION,
                            ANSIBLE_HOSTS.EVENT_SEQ)
                    .values(value((UUID) null), null, null, null, null, null, null, null)
                    .getSQL();

            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                for (HostItem h : hosts) {
                    ps.setObject(1, h.key().instanceId());
                    ps.setObject(2, h.key().instanceCreatedAt());
                    ps.setObject(3, h.key().playbookId());
                    ps.setString(4, StringUtils.abbreviate(h.key().host(), ANSIBLE_HOSTS.HOST.getDataType().length()));
                    ps.setString(5, StringUtils.abbreviate(h.key().hostGroup(), ANSIBLE_HOSTS.HOST_GROUP.getDataType().length()));
                    ps.setString(6, h.status().name());
                    ps.setLong(7, h.duration());
                    ps.setLong(8, h.eventSeq());

                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        private static Field<Integer> decodeStatus(CaseValueStep<String> choose) {
            return choose
                    .when(inline(HostStatus.FAILED.name()), inline(HostStatus.FAILED.weight()))
                    .when(inline(HostStatus.UNREACHABLE.name()), inline(HostStatus.UNREACHABLE.weight()))
                    .when(inline(HostStatus.OK.name()), inline(HostStatus.OK.weight()))
                    .otherwise(inline(HostStatus.OK.weight));
        }
    }

    private static HostStatus getHostStatus(AnsibleEvent e) {
        boolean ignoreErrors = e.ignoreErrors();
        String status = e.getStatus();
        if (ignoreErrors && HostStatus.FAILED.name().equals(status)) {
            return HostStatus.OK;
        }
        return HostStatus.of(status);
    }

    public enum HostStatus {

        OK(2),
        UNREACHABLE(4),
        FAILED(5);

        private final int weight;

        HostStatus(int weight) {
            this.weight = weight;
        }

        public int weight() {
            return weight;
        }

        public static HostStatus of(String status) {
            for (HostStatus s : values()) {
                if (s.name().equals(status)) {
                    return s;
                }
            }
            return OK;
        }
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    public interface HostItem {

        @Value.Immutable
        @Value.Style(jdkOnly = true)
        interface Key {

            @Value.Parameter
            UUID instanceId();

            @Value.Parameter
            OffsetDateTime instanceCreatedAt();

            @Value.Parameter
            UUID playbookId();

            @Value.Parameter
            String host();

            @Value.Parameter
            String hostGroup();

            static Key from(AnsibleEvent e) {
                return ImmutableKey.of(e.instanceId(), e.instanceCreatedAt(), e.playbookId(), e.host(), e.hostGroup());
            }
        }

        Key key();

        HostStatus status();

        long duration();

        long eventSeq();

        static HostItem from(AnsibleEvent event) {
            return ImmutableHostItem.builder()
                    .key(Key.from(event))
                    .status(getHostStatus(event))
                    .duration(event.duration())
                    .eventSeq(event.eventSeq())
                    .build();
        }
    }
}
