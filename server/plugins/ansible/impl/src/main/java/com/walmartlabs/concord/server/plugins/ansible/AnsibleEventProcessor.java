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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.sdk.MapUtils;
import org.immutables.value.Value;
import org.jooq.CaseValueStep;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Field;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.plugins.ansible.jooq.tables.AnsibleHosts.ANSIBLE_HOSTS;
import static org.jooq.impl.DSL.*;

@Named
@Singleton
public class AnsibleEventProcessor implements EventProcessor {

    private final Dao dao;

    @Inject
    public AnsibleEventProcessor(Dao dao) {
        this.dao = dao;
    }

    @Override
    public void process(DSLContext tx, List<Event> events) {
        List<EventItem> eventItems = new ArrayList<>();
        for (Event e : events) {
            if (e.eventType().equals(Constants.ANSIBLE_EVENT_TYPE)) {
                eventItems.add(toEventItem(e));
            }
        }

        List<HostItem> result = combineEvents(eventItems);
        dao.insert(tx, result);
    }

    private EventItem toEventItem(Event e) {
        boolean ignoreErrors = MapUtils.getBoolean(e.payload(), "ignore_errors", false);
        String status = MapUtils.getString(e.payload(), "status");
        if (ignoreErrors && Status.FAILED.name().equals(status)) {
            status = Status.OK.name();
        }

        String hostStatus = MapUtils.getString(e.payload(), "hostStatus");
        if (hostStatus != null) {
            status = hostStatus;
        }

        return ImmutableEventItem.builder()
                .instanceId(e.instanceId())
                .instanceCreatedAt(e.instanceCreatedAt())
                .playbookId(MapUtils.getUUID(e.payload(), "parentCorrelationId"))
                .eventSeq(e.eventSeq())
                .eventDate(e.eventDate())
                .host(MapUtils.assertString(e.payload(), "host"))
                .hostGroup(MapUtils.getString(e.payload(), "hostGroup", "-"))
                .status(status)
                .duration(MapUtils.getNumber(e.payload(), "duration", 0L).longValue())
                .retryCount(MapUtils.getNumber(e.payload(), "currentRetryCount", 0).intValue())
                .build();
    }

    private static List<HostItem> combineEvents(List<EventItem> events) {
        Map<HostItem.Key, HostItem> result = new HashMap<>();
        for (EventItem e : events) {
            result.compute(ImmutableKey.of(e.instanceId(), e.instanceCreatedAt(), e.playbookId(), e.host(), e.hostGroup()),
                    (k, v) -> (v == null) ? HostItem.from(e) : combine(v, e));
        }
        return new ArrayList<>(result.values());
    }

    private static HostItem combine(HostItem hostItem, EventItem newEvent) {
        if (hostItem.retryCount() != newEvent.retryCount()) {
            return HostItem.from(newEvent);
        }

        long duration = hostItem.duration() + newEvent.duration();

        String status;
        long eventSeq;
        if (Status.of(newEvent.status()).weight() > Status.of(hostItem.status()).weight()) {
            status = newEvent.status();
            eventSeq = newEvent.eventSeq();
        } else {
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
            List<HostItem> hosts = removeInvalidItems(items);
            if (hosts.isEmpty()) {
                return;
            }

            DbUtils.upsert(tx, items, Dao::update, Dao::insert);
        }

        private static int[] update(DSLContext tx, Connection conn, List<HostItem> hosts) throws SQLException {
            Field<Integer> currentStatusWeight = decodeStatus(choose(ANSIBLE_HOSTS.STATUS));
            Field<Integer> newStatusWeight = decodeStatus(choose(value((String) null)));

            String update = tx.update(ANSIBLE_HOSTS)
                    .set(ANSIBLE_HOSTS.DURATION, when(ANSIBLE_HOSTS.RETRY_COUNT.notEqual((Integer)null), value((Long)null)).otherwise(ANSIBLE_HOSTS.DURATION.plus(value((Integer) null))))
                    .set(ANSIBLE_HOSTS.STATUS, when(ANSIBLE_HOSTS.RETRY_COUNT.notEqual((Integer)null), value((String)null)).otherwise(when(currentStatusWeight.greaterThan(newStatusWeight), ANSIBLE_HOSTS.STATUS).otherwise(value((String) null))))
                    .set(ANSIBLE_HOSTS.EVENT_SEQ, when(ANSIBLE_HOSTS.RETRY_COUNT.notEqual((Integer)null), value((Long)null)).otherwise(when(currentStatusWeight.greaterThan(newStatusWeight), ANSIBLE_HOSTS.EVENT_SEQ).otherwise(value((Long) null))))
                    .set(ANSIBLE_HOSTS.RETRY_COUNT, (Integer)null)
                    .where(ANSIBLE_HOSTS.INSTANCE_ID.eq(value((UUID) null))
                            .and(ANSIBLE_HOSTS.INSTANCE_CREATED_AT.eq(value((Timestamp) null))
                                    .and(ANSIBLE_HOSTS.HOST.eq(value((String) null))
                                            .and(ANSIBLE_HOSTS.HOST_GROUP.eq(value((String) null))))))
                    .getSQL();

            try (PreparedStatement ps = conn.prepareStatement(update)) {
                for (HostItem h : hosts) {
                    // set duration
                    ps.setInt(1, h.retryCount());
                    ps.setLong(2, h.duration());
                    ps.setLong(3, h.duration());

                    // set status
                    ps.setInt(4, h.retryCount());
                    ps.setString(5, h.status());
                    ps.setString(6, h.status());
                    ps.setString(7, h.status());

                    // set event seq
                    ps.setInt(8, h.retryCount());
                    ps.setLong(9, h.eventSeq());
                    ps.setString(10, h.status());
                    ps.setLong(11, h.eventSeq());

                    //set retry count
                    ps.setInt(12, h.retryCount());

                    ps.setObject(13, h.key().instanceId());
                    ps.setTimestamp(14, h.key().instanceCreatedAt());
                    ps.setString(15, h.key().host());
                    ps.setString(16, h.key().hostGroup());

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
                            ANSIBLE_HOSTS.EVENT_SEQ,
                            ANSIBLE_HOSTS.RETRY_COUNT)
                    .values(value((UUID) null), null, null, null, null, null, null, null, null)
                    .getSQL();

            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                for (HostItem h : hosts) {
                    ps.setObject(1, h.key().instanceId());
                    ps.setTimestamp(2, h.key().instanceCreatedAt());
                    ps.setObject(3, h.key().playbookId());
                    ps.setString(4, h.key().host());
                    ps.setString(5, h.key().hostGroup());
                    ps.setString(6, h.status());
                    ps.setLong(7, h.duration());
                    ps.setLong(8, h.eventSeq());
                    ps.setLong(9, h.retryCount());

                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        private static Field<Integer> decodeStatus(CaseValueStep<String> choose) {
            return choose
                    .when(inline(Status.FAILED.name()), inline(Status.FAILED.weight()))
                    .when(inline(Status.UNREACHABLE.name()), inline(Status.UNREACHABLE.weight()))
                    .when(inline(Status.SKIPPED.name()), inline(Status.SKIPPED.weight()))
                    .when(inline(Status.CHANGED.name()), inline(Status.CHANGED.weight()))
                    .when(inline(Status.OK.name()), inline(Status.OK.weight()))
                    .otherwise(inline(0));
        }

        private static List<HostItem> removeInvalidItems(List<HostItem> items) {
            return items.stream()
                    .filter(i -> i.key().host().length() < ANSIBLE_HOSTS.HOST.getDataType().length())
                    .filter(i -> i.key().hostGroup().length() < ANSIBLE_HOSTS.HOST_GROUP.getDataType().length())
                    .collect(Collectors.toList());

        }
    }

    private enum Status {

        RUNNING(0),
        SKIPPED(1),
        OK(2),
        CHANGED(3),
        UNREACHABLE(4),
        FAILED(5);

        private final int weight;

        Status(int weight) {
            this.weight = weight;
        }

        public int weight() {
            return weight;
        }

        public static Status of(String status) {
            for (Status s : values()) {
                if (s.name().equals(status)) {
                    return s;
                }
            }
            return OK;
        }
    }

    @Value.Immutable
    public interface HostItem {

        @Value.Immutable
        interface Key {

            @Value.Parameter
            UUID instanceId();

            @Value.Parameter
            Timestamp instanceCreatedAt();

            @Value.Parameter
            @Nullable
            UUID playbookId();

            @Value.Parameter
            String host();

            @Value.Parameter
            String hostGroup();
        }

        Key key();

        String status();

        long duration();

        long eventSeq();

        int retryCount();

        static HostItem from(EventItem event) {
            return ImmutableHostItem.builder()
                    .key(ImmutableKey.of(event.instanceId(), event.instanceCreatedAt(), event.playbookId(), event.host(), event.hostGroup()))
                    .status(event.status())
                    .duration(event.duration())
                    .eventSeq(event.eventSeq())
                    .retryCount(event.retryCount())
                    .build();
        }
    }

    @Value.Immutable
    public interface EventItem extends AbstractEventProcessor.Event {

        UUID instanceId();

        Timestamp instanceCreatedAt();

        @Nullable
        UUID playbookId();

        Timestamp eventDate();

        long eventSeq();

        String host();

        String hostGroup();

        String status();

        long duration();

        int retryCount();
    }
}
