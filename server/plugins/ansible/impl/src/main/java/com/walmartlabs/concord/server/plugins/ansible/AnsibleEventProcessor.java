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
import com.walmartlabs.concord.server.jooq.tables.ProcessEvents;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.immutables.value.Value;
import org.jooq.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_EVENTS;
import static com.walmartlabs.concord.server.plugins.ansible.EventMarkerDao.EventMarker;
import static com.walmartlabs.concord.server.plugins.ansible.jooq.tables.AnsibleHosts.ANSIBLE_HOSTS;
import static org.jooq.impl.DSL.*;

@Named("ansible-event-processor")
@Singleton
public class AnsibleEventProcessor extends AbstractEventProcessor<AnsibleEventProcessor.EventItem> {

    private static final Logger log = LoggerFactory.getLogger(AnsibleEventProcessor.class);

    private static final String PROCESSOR_NAME = "ansible-event-processor";

    private final AnsibleEventsConfiguration cfg;
    private final AnsibleEventDao dao;

    @Inject
    public AnsibleEventProcessor(AnsibleEventsConfiguration cfg, EventMarkerDao eventMarkerDao, AnsibleEventDao dao) {
        super(PROCESSOR_NAME, eventMarkerDao, cfg.getFetchLimit());
        this.cfg = cfg;
        this.dao = dao;
    }

    @Override
    public long getIntervalInSec() {
        return cfg.getPeriod();
    }

    @Override
    protected List<EventItem> processEvents(DSLContext tx, EventMarker marker, int fetchLimit) {
        List<EventItem> events = dao.list(tx, marker, fetchLimit);
        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<HostItem> result = combineEvents(events);
        dao.insert(tx, result);
        return events;
    }

    private static List<HostItem> combineEvents(List<EventItem> events) {
        Map<HostItem.Key, HostItem> result = new HashMap<>();
        for (EventItem e : events) {
            result.compute(ImmutableKey.of(e.instanceId(), e.instanceCreatedAt(), e.host(), e.hostGroup()),
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
    public static class AnsibleEventDao extends AbstractDao {

        @Inject
        public AnsibleEventDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        @Override
        public <T> T txResult(TxResult<T> t) {
            return super.txResult(t);
        }

        @WithTimer
        public List<EventItem> list(DSLContext tx, EventMarker marker, int count) {
            ProcessEvents pe = PROCESS_EVENTS.as("pe");
            SelectConditionStep<Record11<UUID, Timestamp, Long, Timestamp, String, String, String, Long, Boolean, Integer, String>> q = tx.select(
                    pe.INSTANCE_ID,
                    pe.INSTANCE_CREATED_AT,
                    pe.EVENT_SEQ,
                    pe.EVENT_DATE,
                    field("{0}->>'host'", String.class, pe.EVENT_DATA),
                    coalesce(field("{0}->>'hostGroup'", String.class, pe.EVENT_DATA), value("-")),
                    field("{0}->>'status'", String.class, pe.EVENT_DATA),
                    coalesce(field("{0}->>'duration'", Long.class, pe.EVENT_DATA), value("0")),
                    coalesce(field("{0}->>'ignore_errors'", Boolean.class, pe.EVENT_DATA), value("false")),
                    coalesce(field("{0}->>'currentRetryCount'", Integer.class, pe.EVENT_DATA), value("0")),
                    field("{0}->>'hostStatus'", String.class, pe.EVENT_DATA))
                    .from(pe)
                    .where(pe.EVENT_TYPE.eq(Constants.EVENT_TYPE)
                            .and(pe.INSTANCE_CREATED_AT.greaterOrEqual(marker.instanceCreatedStart())
                                    .and(pe.INSTANCE_CREATED_AT.lessThan(marker.instanceCreatedEnd())
                                            .and(pe.EVENT_SEQ.greaterThan(marker.eventSeq())))));

            return q.orderBy(pe.EVENT_SEQ)
                    .limit(count)
                    .fetch(AnsibleEventDao::toEntity);
        }

        private static EventItem toEntity(Record11<UUID, Timestamp, Long, Timestamp, String, String, String, Long, Boolean, Integer, String> r) {
            boolean ignoreErrors = Boolean.TRUE.equals(r.value9());
            String status = r.value7();
            if (ignoreErrors && Status.FAILED.name().equals(status)) {
                status = Status.OK.name();
            }

            String hostStatus = r.value11();
            if (hostStatus != null) {
                status = hostStatus;
            }

            return ImmutableEventItem.builder()
                    .instanceId(r.value1())
                    .instanceCreatedAt(r.value2())
                    .eventSeq(r.value3())
                    .eventDate(r.value4())
                    .host(r.value5())
                    .hostGroup(r.value6())
                    .status(status)
                    .duration(r.value8())
                    .retryCount(r.value10())
                    .build();
        }

        @WithTimer
        public void insert(DSLContext tx, List<HostItem> items) {
            List<HostItem> hosts = removeInvalidItems(items);
            if (hosts.isEmpty()) {
                return;
            }
            tx.connection(conn -> {
                int[] updated = update(tx, conn, hosts);
                List<HostItem> hostsForInsert = new ArrayList<>();
                for (int i = 0; i < updated.length; i++) {
                    if (updated[i] < 1) {
                        hostsForInsert.add(hosts.get(i));
                    }
                }
                if (!hostsForInsert.isEmpty()) {
                    insert(tx, conn, hostsForInsert);
                }
                log.debug("insert -> updated: {}, inserted: {}", hosts.size() - hostsForInsert.size(), hostsForInsert.size());
            });
        }

        private int[] update(DSLContext tx, Connection conn, List<HostItem> hosts) throws SQLException {
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

        private void insert(DSLContext tx, Connection conn, List<HostItem> hosts) throws SQLException {
            String insert = tx.insertInto(ANSIBLE_HOSTS)
                    .columns(ANSIBLE_HOSTS.INSTANCE_ID,
                            ANSIBLE_HOSTS.INSTANCE_CREATED_AT,
                            ANSIBLE_HOSTS.HOST,
                            ANSIBLE_HOSTS.HOST_GROUP,
                            ANSIBLE_HOSTS.STATUS,
                            ANSIBLE_HOSTS.DURATION,
                            ANSIBLE_HOSTS.EVENT_SEQ,
                            ANSIBLE_HOSTS.RETRY_COUNT)
                    .values(value((UUID) null), null, null, null, null, null, null, null)
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
                    ps.setLong(8, h.retryCount());

                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        private Field<Integer> decodeStatus(CaseValueStep<String> choose) {
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
                    .key(ImmutableKey.of(event.instanceId(), event.instanceCreatedAt(), event.host(), event.hostGroup()))
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

        Timestamp eventDate();

        long eventSeq();

        String host();

        String hostGroup();

        String status();

        long duration();

        int retryCount();
    }
}
