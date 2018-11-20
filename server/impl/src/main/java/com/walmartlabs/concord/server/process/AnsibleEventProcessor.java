package com.walmartlabs.concord.server.process;

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
import com.walmartlabs.concord.sdk.EventType;
import com.walmartlabs.concord.server.cfg.AnsibleEventsConfiguration;
import com.walmartlabs.concord.server.jooq.tables.ProcessEvents;
import com.walmartlabs.concord.server.task.ScheduledTask;
import org.immutables.value.Value;
import org.jooq.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_EVENTS;
import static com.walmartlabs.concord.server.jooq.tables.AnsibleHosts.ANSIBLE_HOSTS;
import static com.walmartlabs.concord.server.jooq.tables.EventProcessorMarkers.EVENT_PROCESSOR_MARKERS;
import static org.jooq.impl.DSL.*;

@Named("ansible-event-processor")
@Singleton
public class AnsibleEventProcessor implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(AnsibleEventProcessor.class);
    private static final String PROCESSOR_NAME = "ansible-event-processor";

    private final AnsibleEventsConfiguration cfg;
    private final AnsibleEventDao dao;

    @Inject
    public AnsibleEventProcessor(AnsibleEventsConfiguration cfg, AnsibleEventDao dao) {
        this.cfg = cfg;
        this.dao = dao;
    }

    @Override
    public long getIntervalInSec() {
        return cfg.getPeriod();
    }

    public void performTask() {
        dao.tx(tx -> {
            EventMarker marker = dao.getMarker(PROCESSOR_NAME);

            List<EventItem> events = dao.list(tx, marker.instanceCreatedAt(), marker.eventSeq(), cfg.getFetchLimit());
            if (events.isEmpty()) {
                return;
            }

            List<HostItem> result = processEvents(events);
            dao.insert(tx, result);

            EventItem lastEvent = events.get(events.size() - 1);
            dao.updateMarker(tx, PROCESSOR_NAME, ImmutableEventMarker.of(lastEvent.instanceCreatedAt(), lastEvent.eventSeq()));
        });
    }

    private List<HostItem> processEvents(List<EventItem> events) {
        Map<Key, HostItem> result = new HashMap<>();
        for (EventItem e : events) {
            result.compute(ImmutableKey.of(e.instanceId(), e.instanceCreatedAt(), e.host(), e.hostGroup()), (k, v) -> (v == null) ? HostItem.from(e) : combine(v, e));
        }
        return new ArrayList<>(result.values());
    }

    private static HostItem combine(HostItem hostItem, EventItem newEvent) {
        long duration = hostItem.duration() + newEvent.duration();

        String status;
        long eventSeq;
        if (Status.of(newEvent.status()).weight > Status.of(hostItem.status()).weight) {
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
    public static class AnsibleEventDao extends AbstractDao {

        @Inject
        public AnsibleEventDao(@Named("app") Configuration cfg) {
            super(cfg);
        }

        @Override
        public void tx(Tx t) {
            super.tx(t);
        }

        @SuppressWarnings("deprecation")
        public List<EventItem> list(DSLContext tx, Timestamp instanceCreatedAt, Long startEventSeq, int count) {
            ProcessEvents pe = PROCESS_EVENTS.as("pe");
            SelectConditionStep<Record8<UUID, Timestamp, Long, String, String, String, Long, Boolean>> q = tx.select(
                    pe.INSTANCE_ID,
                    pe.INSTANCE_CREATED_AT,
                    pe.EVENT_SEQ,
                    field("{0}->>'host'", String.class, pe.EVENT_DATA),
                    coalesce(field("{0}->>'hostGroup'", String.class, pe.EVENT_DATA), value("-")),
                    field("{0}->>'status'", String.class, pe.EVENT_DATA),
                    coalesce(field("{0}->>'duration'", Long.class, pe.EVENT_DATA), value("0")),
                    coalesce(field("{0}->>'ignore_errors'", Boolean.class, pe.EVENT_DATA), value("false")))
            .from(pe)
            .where(pe.EVENT_TYPE.eq(EventType.ANSIBLE.name()));

            if (instanceCreatedAt != null && startEventSeq != null) {
                q.and(pe.INSTANCE_CREATED_AT.greaterOrEqual(instanceCreatedAt))
                        .and(pe.EVENT_SEQ.greaterThan(startEventSeq));
            }

            return q.orderBy(pe.EVENT_SEQ)
                    .limit(count)
                    .fetch(AnsibleEventDao::toEntity);
        }

        private static EventItem toEntity(Record8<UUID, Timestamp, Long, String, String, String, Long, Boolean> r) {
            boolean ignoreErrors = Boolean.TRUE.equals(r.value8());
            String status = r.value6();
            if (ignoreErrors && Status.FAILED.name().equals(status)) {
                status = Status.OK.name();
            }

            return ImmutableEventItem.builder()
                    .instanceId(r.value1())
                    .instanceCreatedAt(r.value2())
                    .eventSeq(r.value3())
                    .host(r.value4())
                    .hostGroup(r.value5())
                    .status(status)
                    .duration(r.value7())
                    .build();
        }

        public void insert(DSLContext tx, List<HostItem> hosts) {
            tx.connection(conn -> {
                int[] updated = update(tx, conn, hosts);
                List<HostItem> hostsForInsert = new ArrayList<>();
                for (int i = 0; i < updated.length; i++) {
                    if (updated[i] < 1) {
                        hostsForInsert.add(hosts.get(i));
                    }
                }
                if(!hostsForInsert.isEmpty()) {
                    insert(tx, conn, hostsForInsert);
                }
                log.debug("insert -> updated: {}, inserted: {}", hosts.size() - hostsForInsert.size(), hostsForInsert.size());
            });
        }

        public void updateMarker(DSLContext tx, String processorName, EventMarker marker) {
            tx.update(EVENT_PROCESSOR_MARKERS)
                    .set(EVENT_PROCESSOR_MARKERS.INSTANCE_CREATED_AT, value(marker.instanceCreatedAt()))
                    .set(EVENT_PROCESSOR_MARKERS.EVENT_SEQ, value(marker.eventSeq()))
                    .where(EVENT_PROCESSOR_MARKERS.PROCESSOR_NAME.eq(processorName))
                    .execute();
        }

        public EventMarker getMarker(String processorName) {
            return txResult(tx -> tx.select(EVENT_PROCESSOR_MARKERS.INSTANCE_CREATED_AT, EVENT_PROCESSOR_MARKERS.EVENT_SEQ)
                    .from(EVENT_PROCESSOR_MARKERS)
                    .where(EVENT_PROCESSOR_MARKERS.PROCESSOR_NAME.eq(processorName))
                    .fetchOne(r -> ImmutableEventMarker.of(r.value1(), r.value2())));
        }

        private int[] update(DSLContext tx, Connection conn, List<HostItem> hosts) throws SQLException  {
            Field<Integer> currentStatusWeight = decodeStatus(choose(ANSIBLE_HOSTS.STATUS));
            Field<Integer> newStatusWeight = decodeStatus(choose(value((String)null)));

            String update = tx.update(ANSIBLE_HOSTS)
                    .set(ANSIBLE_HOSTS.DURATION, ANSIBLE_HOSTS.DURATION.plus(value((Integer)null)))
                    .set(ANSIBLE_HOSTS.STATUS, when(currentStatusWeight.greaterThan(newStatusWeight), ANSIBLE_HOSTS.STATUS).otherwise(value((String)null)))
                    .set(ANSIBLE_HOSTS.EVENT_SEQ, when(currentStatusWeight.greaterThan(newStatusWeight), ANSIBLE_HOSTS.EVENT_SEQ).otherwise(value((Long)null)))
                    .where(ANSIBLE_HOSTS.INSTANCE_ID.eq(value((UUID)null))
                            .and(ANSIBLE_HOSTS.INSTANCE_CREATED_AT.eq(value((Timestamp)null))
                                    .and(ANSIBLE_HOSTS.HOST.eq(value((String)null))
                                            .and(ANSIBLE_HOSTS.HOST_GROUP.eq(value((String)null))))))
                    .getSQL();

            try (PreparedStatement ps = conn.prepareStatement(update)) {
                for (HostItem h : hosts) {
                    ps.setLong(1, h.duration());
                    ps.setString(2, h.status());
                    ps.setString(3, h.status());
                    ps.setString(4, h.status());
                    ps.setLong(5, h.eventSeq());
                    ps.setObject(6, h.key().instanceId());
                    ps.setTimestamp(7, h.key().instanceCreatedAt());
                    ps.setString(8, h.key().host());
                    ps.setString(9, h.key().hostGroup());

                    ps.addBatch();
                }
                return ps.executeBatch();
            }
        }

        private void insert(DSLContext tx, Connection conn, List<HostItem> hosts) throws SQLException {
            String insert = tx.insertInto(ANSIBLE_HOSTS)
                    .columns(ANSIBLE_HOSTS.INSTANCE_ID,
                            ANSIBLE_HOSTS.INSTANCE_CREATED_AT,
                            ANSIBLE_HOSTS.HOST,
                            ANSIBLE_HOSTS.HOST_GROUP,
                            ANSIBLE_HOSTS.STATUS,
                            ANSIBLE_HOSTS.DURATION,
                            ANSIBLE_HOSTS.EVENT_SEQ)
                    .values(value((UUID) null), null, null, null, null, null, null)
                    .getSQL();

            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                for (HostItem h : hosts) {
                    ps.setObject(1, h.key().instanceId());
                    ps.setTimestamp(2, h.key().instanceCreatedAt());
                    ps.setString(3, h.key().host());
                    ps.setString(4, h.key().hostGroup());
                    ps.setString(5, h.status());
                    ps.setLong(6, h.duration());
                    ps.setLong(7, h.eventSeq());

                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        private Field<Integer> decodeStatus(CaseValueStep<String> choose) {
            return choose
                    .when(inline(Status.FAILED.name()), inline(Status.FAILED.weight))
                    .when(inline(Status.UNREACHABLE.name()), inline(Status.UNREACHABLE.weight))
                    .when(inline(Status.SKIPPED.name()), inline(Status.SKIPPED.weight))
                    .when(inline(Status.CHANGED.name()), inline(Status.CHANGED.weight))
                    .when(inline(Status.OK.name()), inline(Status.OK.weight))
                    .otherwise(inline(0));
        }
    }

    private enum Status {

        RUNNING(0),
        OK(1),
        CHANGED(2),
        SKIPPED(3),
        UNREACHABLE(4),
        FAILED(5);

        private final int weight;

        Status(int weight) {
            this.weight = weight;
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
    public interface Key {
        @Value.Parameter
        UUID instanceId();

        @Value.Parameter
        Timestamp instanceCreatedAt();

        @Value.Parameter
        String host();

        @Value.Parameter
        String hostGroup();
    }

    @Value.Immutable
    public interface HostItem {

        Key key();
        String status();
        long duration();
        long eventSeq();

        static HostItem from(EventItem event) {
            return ImmutableHostItem.builder()
                    .key(ImmutableKey.of(event.instanceId(), event.instanceCreatedAt(), event.host(), event.hostGroup()))
                    .status(event.status())
                    .duration(event.duration())
                    .eventSeq(event.eventSeq())
                    .build();
        }
    }

    @Value.Immutable
    public interface EventItem {

        UUID instanceId();
        Timestamp instanceCreatedAt();
        long eventSeq();
        String host();
        String hostGroup();
        String status();
        long duration();
    }

    @Value.Immutable
    public interface EventMarker {

        @Value.Parameter
        @Nullable
        Timestamp instanceCreatedAt();

        @Value.Parameter
        @Nullable
        Long eventSeq();
    }
}
