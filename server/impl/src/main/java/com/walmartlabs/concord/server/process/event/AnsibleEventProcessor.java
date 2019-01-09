package com.walmartlabs.concord.server.process.event;

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
import com.walmartlabs.concord.server.jooq.tables.EventProcessorMarker;
import com.walmartlabs.concord.server.jooq.tables.ProcessEvents;
import com.walmartlabs.concord.server.process.ProcessStatus;
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
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.jooq.tables.AnsibleHosts.ANSIBLE_HOSTS;
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
        boolean continueProcess;

        do {
            continueProcess = false;

            List<EventMarker> markers = dao.listMarkers(PROCESSOR_NAME);
            if (markers.isEmpty()) {
                EventMarker m = createInitMarker();
                if (m == null) {
                    return;
                }
            }

            for (EventMarker m : markers) {
                int processedEvents = process(m, cfg.getFetchLimit());
                if (processedEvents >= cfg.getFetchLimit()) {
                    continueProcess = true;
                }
            }
        } while (continueProcess);

        dao.cleanUpMarkers(PROCESSOR_NAME);
    }

    private EventMarker createInitMarker() {
        Timestamp firstProcess = dao.getFirstProcessDate();
        if (firstProcess == null) {
            return null;
        }

        EventMarker m = EventMarker.builder()
                .startFrom(startOfDay(firstProcess))
                .eventDate(firstProcess)
                .eventSeq(-1)
                .build();
        dao.updateMarker(PROCESSOR_NAME, m, MarkerStatus.IN_PROCESS);

        return m;
    }

    private int process(EventMarker marker, int fetchLimit) {
        if (marker.endTo() != null) {
            return processOldMarker(marker, fetchLimit);
        } else {
            return processActiveMarker(marker, fetchLimit);
        }
    }

    private int processActiveMarker(EventMarker marker, int fetchLimit) {
        return dao.txResult(tx -> {
            List<EventItem> events = processEvents(tx, marker, fetchLimit);
            if (events.isEmpty()) {
                return 0;
            }

            List<EventMarker> markers = collectMarkers(marker, events);
            dao.updateMarkers(tx, PROCESSOR_NAME, markers, MarkerStatus.IN_PROCESS);

            return events.size();
        });
    }

    private int processOldMarker(EventMarker marker, int fetchLimit) {
        return dao.txResult(tx -> {
            boolean hasActiveProcesses = dao.hasActiveProcess(tx, marker.startFrom(), marker.endTo());
            List<EventItem> events = processEvents(tx, marker, fetchLimit);
            if (events.isEmpty()) {
                if (!hasActiveProcesses) {
                    dao.updateMarker(tx, PROCESSOR_NAME, marker, MarkerStatus.DONE);
                }

                return 0;
            }

            EventItem lastEvent = events.get(events.size() - 1);
            dao.updateMarker(tx, PROCESSOR_NAME, EventMarker.builder().from(marker)
                            .eventDate(lastEvent.eventDate())
                            .eventSeq(lastEvent.eventSeq())
                            .build(),
                    MarkerStatus.IN_PROCESS);

            return events.size();
        });
    }

    private List<EventItem> processEvents(DSLContext tx, EventMarker marker, int fetchLimit) {
        List<EventItem> events = dao.list(tx, marker, fetchLimit);
        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<HostItem> result = combineEvents(events);
        dao.insert(tx, result);
        return events;
    }

    private List<EventMarker> collectMarkers(EventMarker marker, List<EventItem> events) {
        Map<Timestamp, ImmutableEventMarker.Builder> result = new HashMap<>();
        result.put(marker.startFrom(), EventMarker.builder().from(marker));
        for (EventItem e : events) {
            Timestamp startFrom = startOfDay(e.instanceCreatedAt());

            ImmutableEventMarker.Builder currentMarker = result.computeIfAbsent(startFrom, t -> EventMarker.builder().startFrom(t));
            currentMarker.eventDate(e.eventDate())
                    .eventSeq(e.eventSeq());
        }

        List<Timestamp> starts = result.keySet().stream().sorted(Timestamp::compareTo).collect(Collectors.toList());
        for (int i = 0; i < starts.size() - 1; i++) {
            result.get(starts.get(i)).endTo(starts.get(i + 1));
        }

        return result.values().stream()
                .map(ImmutableEventMarker.Builder::build)
                .collect(Collectors.toList());
    }

    private List<HostItem> combineEvents(List<EventItem> events) {
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

    private static Timestamp startOfDay(Timestamp ts) {
        return Timestamp.valueOf(ts.toLocalDateTime().toLocalDate().atTime(LocalTime.MIN));
    }

    @Named
    public static class AnsibleEventDao extends AbstractDao {

        @Inject
        public AnsibleEventDao(@Named("app") Configuration cfg) {
            super(cfg);
        }

        @Override
        public <T> T txResult(TxResult<T> t) {
            return super.txResult(t);
        }

        public List<EventItem> list(DSLContext tx, EventMarker marker, int count) {
            ProcessEvents pe = PROCESS_EVENTS.as("pe");
            SelectConditionStep<Record9<UUID, Timestamp, Long, Timestamp, String, String, String, Long, Boolean>> q = tx.select(
                    pe.INSTANCE_ID,
                    pe.INSTANCE_CREATED_AT,
                    pe.EVENT_SEQ,
                    pe.EVENT_DATE,
                    field("{0}->>'host'", String.class, pe.EVENT_DATA),
                    coalesce(field("{0}->>'hostGroup'", String.class, pe.EVENT_DATA), value("-")),
                    field("{0}->>'status'", String.class, pe.EVENT_DATA),
                    coalesce(field("{0}->>'duration'", Long.class, pe.EVENT_DATA), value("0")),
                    coalesce(field("{0}->>'ignore_errors'", Boolean.class, pe.EVENT_DATA), value("false")))
                    .from(pe)
                    .where(pe.EVENT_TYPE.eq(EventType.ANSIBLE.name()));

            if (marker != null) {
                q.and(pe.EVENT_DATE.greaterOrEqual(marker.eventDate()))
                        .and(pe.INSTANCE_CREATED_AT.greaterOrEqual(marker.startFrom())
                                .and(pe.EVENT_SEQ.greaterThan(marker.eventSeq())));

                if (marker.endTo() != null) {
                    q.and(pe.INSTANCE_CREATED_AT.lessThan(marker.endTo()));
                }
            }

            return q.orderBy(pe.EVENT_SEQ)
                    .limit(count)
                    .fetch(AnsibleEventDao::toEntity);
        }

        private static EventItem toEntity(Record9<UUID, Timestamp, Long, Timestamp, String, String, String, Long, Boolean> r) {
            boolean ignoreErrors = Boolean.TRUE.equals(r.value9());
            String status = r.value7();
            if (ignoreErrors && Status.FAILED.name().equals(status)) {
                status = Status.OK.name();
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
                    .build();
        }

        public Timestamp getFirstProcessDate() {
            return txResult(tx -> tx.select(PROCESS_QUEUE.CREATED_AT)
                    .from(PROCESS_QUEUE)
                    .orderBy(PROCESS_QUEUE.CREATED_AT)
                    .limit(1)
                    .fetchOne(Record1::value1));
        }

        public boolean hasActiveProcess(DSLContext tx, Timestamp fromTime, Timestamp toTime) {
            int count = tx.selectCount()
                    .from(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.CREATED_AT.between(fromTime, toTime)
                            .and(PROCESS_QUEUE.CURRENT_STATUS.notIn(ProcessStatus.FINISHED.name(),
                                    ProcessStatus.FAILED.name(),
                                    ProcessStatus.CANCELLED.name(),
                                    ProcessStatus.TIMED_OUT.name())))
                    .fetchOne(Record1::value1);
            return count > 0;
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
                if (!hostsForInsert.isEmpty()) {
                    insert(tx, conn, hostsForInsert);
                }
                log.debug("insert -> updated: {}, inserted: {}", hosts.size() - hostsForInsert.size(), hostsForInsert.size());
            });
        }

        public void updateMarkers(DSLContext tx, String processorName, List<EventMarker> markers, MarkerStatus status) {
            for (EventMarker m : markers) {
                updateMarker(tx, processorName, m, status);
            }
        }

        public void updateMarker(String processorName, EventMarker marker, MarkerStatus status) {
            tx(tx -> updateMarker(tx, processorName, marker, status));
        }

        public void updateMarker(DSLContext tx, String processorName, EventMarker marker, MarkerStatus status) {
            EventProcessorMarker e = EVENT_PROCESSOR_MARKER.as("e");
            tx.insertInto(e)
                    .columns(e.PROCESSOR_NAME, e.START_FROM, e.END_TO, e.EVENT_DATE, e.EVENT_SEQ, e.STATUS)
                    .values(processorName, marker.startFrom(), marker.endTo(), marker.eventDate(), marker.eventSeq(), status.name())
                    .onDuplicateKeyUpdate()
                    .set(e.START_FROM, value(marker.startFrom()))
                    .set(e.END_TO, value(marker.endTo()))
                    .set(e.EVENT_DATE, value(marker.eventDate()))
                    .set(e.EVENT_SEQ, value(marker.eventSeq()))
                    .set(e.STATUS, value(status.name()))
                    .where(e.PROCESSOR_NAME.eq(processorName))
                    .execute();
        }

        public void cleanUpMarkers(String processorName) {
            tx(tx -> {
                tx.deleteFrom(EVENT_PROCESSOR_MARKER)
                        .where(EVENT_PROCESSOR_MARKER.PROCESSOR_NAME.eq(processorName)
                                .and(EVENT_PROCESSOR_MARKER.STATUS.eq(MarkerStatus.DONE.name())))
                        .execute();
            });
        }

        public List<EventMarker> listMarkers(String processorName) {
            return txResult(tx -> tx.selectFrom(EVENT_PROCESSOR_MARKER)
                    .where(EVENT_PROCESSOR_MARKER.PROCESSOR_NAME.eq(processorName)
                            .and(EVENT_PROCESSOR_MARKER.STATUS.eq(MarkerStatus.IN_PROCESS.name())))
                    .orderBy(EVENT_PROCESSOR_MARKER.START_FROM.desc()))
                    .fetch(e -> EventMarker.builder()
                            .startFrom(e.getStartFrom())
                            .endTo(e.getEndTo())
                            .eventDate(e.getEventDate())
                            .eventSeq(e.getEventSeq())
                            .build());
        }

        private int[] update(DSLContext tx, Connection conn, List<HostItem> hosts) throws SQLException {
            Field<Integer> currentStatusWeight = decodeStatus(choose(ANSIBLE_HOSTS.STATUS));
            Field<Integer> newStatusWeight = decodeStatus(choose(value((String) null)));

            String update = tx.update(ANSIBLE_HOSTS)
                    .set(ANSIBLE_HOSTS.DURATION, ANSIBLE_HOSTS.DURATION.plus(value((Integer) null)))
                    .set(ANSIBLE_HOSTS.STATUS, when(currentStatusWeight.greaterThan(newStatusWeight), ANSIBLE_HOSTS.STATUS).otherwise(value((String) null)))
                    .set(ANSIBLE_HOSTS.EVENT_SEQ, when(currentStatusWeight.greaterThan(newStatusWeight), ANSIBLE_HOSTS.EVENT_SEQ).otherwise(value((Long) null)))
                    .where(ANSIBLE_HOSTS.INSTANCE_ID.eq(value((UUID) null))
                            .and(ANSIBLE_HOSTS.INSTANCE_CREATED_AT.eq(value((Timestamp) null))
                                    .and(ANSIBLE_HOSTS.HOST.eq(value((String) null))
                                            .and(ANSIBLE_HOSTS.HOST_GROUP.eq(value((String) null))))))
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
        SKIPPED(1),
        OK(2),
        CHANGED(3),
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

        Timestamp eventDate();

        long eventSeq();

        String host();

        String hostGroup();

        String status();

        long duration();
    }

    @Value.Immutable
    public interface EventMarker {

        @Value.Parameter
        Timestamp startFrom();

        @Nullable
        @Value.Parameter
        Timestamp endTo();

        @Value.Parameter
        Timestamp eventDate();

        @Value.Parameter
        long eventSeq();

        static ImmutableEventMarker.Builder builder() {
            return ImmutableEventMarker.builder();
        }
    }

    public enum MarkerStatus {
        IN_PROCESS,
        DONE
    }
}
