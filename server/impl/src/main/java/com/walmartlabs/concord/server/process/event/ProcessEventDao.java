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
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.sdk.EventType;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.jooq.tables.ProcessEventStats;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessEventsRecord;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Statement;
import java.sql.*;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_EVENTS;
import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_EVENT_STATS;
import static org.jooq.impl.DSL.*;

@Named
public class ProcessEventDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public ProcessEventDao(@MainDB Configuration cfg, ConcordObjectMapper objectMapper) {
        super(cfg);
        this.objectMapper = objectMapper;
    }

    public List<ProcessEventEntry> list(ProcessEventFilter filter) {
        try (DSLContext tx = DSL.using(cfg)) {

            ProcessKey processKey = filter.processKey();

            SelectConditionStep<Record5<Long, UUID, String, Timestamp, JSONB>> q = tx
                    .select(PROCESS_EVENTS.EVENT_SEQ,
                            PROCESS_EVENTS.EVENT_ID,
                            PROCESS_EVENTS.EVENT_TYPE,
                            PROCESS_EVENTS.EVENT_DATE,
                            function("jsonb_strip_nulls", JSONB.class, PROCESS_EVENTS.EVENT_DATA))
                    .from(PROCESS_EVENTS)
                    .where(PROCESS_EVENTS.INSTANCE_ID.eq(processKey.getInstanceId())
                            .and(PROCESS_EVENTS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())));

            Timestamp after = filter.after();
            if (after != null) {
                q.and(PROCESS_EVENTS.EVENT_DATE.ge(after));
            }

            Long fromId = filter.fromId();
            if (fromId != null) {
                q.and(PROCESS_EVENTS.EVENT_SEQ.greaterThan(fromId));
            }

            String eventType = filter.eventType();
            if (eventType != null) {
                q.and(PROCESS_EVENTS.EVENT_TYPE.eq(eventType));
            }

            UUID eventCorrelationId = filter.eventCorrelationId();
            if (eventCorrelationId != null) {
                q.and(PgUtils.jsonText(PROCESS_EVENTS.EVENT_DATA, "correlationId").eq(eventCorrelationId.toString()));
            }

            EventPhase eventPhase = filter.eventPhase();
            if (eventPhase != null) {
                q.and(PgUtils.jsonText(PROCESS_EVENTS.EVENT_DATA, "phase").eq(eventPhase.getKey()));
            }

            int limit = filter.limit();
            if (limit > 0) {
                q.limit(limit);
            }

            return q.orderBy(PROCESS_EVENTS.EVENT_SEQ)
                    .fetch(this::toEntry);
        }
    }

    public void insert(ProcessKey processKey, String eventType, OffsetDateTime eventDate, Map<String, Object> data) {
        tx(tx -> insert(tx, processKey, eventType, eventDate, data));
    }

    public void insert(ProcessKey processKey, List<ProcessEventRequest> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        tx(tx -> insert(tx, processKey, entries));
    }

    public void insert(DSLContext tx, ProcessKey processKey, String eventType, OffsetDateTime eventDate, Map<String, Object> data) {
        // client have option to send the actual event's timestamp
        // if it's not available then the current DB timestamp will be used
        Field<Timestamp> ts = eventDate != null ? value(Timestamp.from(eventDate.toInstant())) : currentTimestamp();

        ProcessEventsRecord r = tx.insertInto(PROCESS_EVENTS)
                .set(PROCESS_EVENTS.INSTANCE_ID, processKey.getInstanceId())
                .set(PROCESS_EVENTS.INSTANCE_CREATED_AT, processKey.getCreatedAt())
                .set(PROCESS_EVENTS.EVENT_TYPE, eventType)
                .set(PROCESS_EVENTS.EVENT_DATE, ts)
                .set(PROCESS_EVENTS.EVENT_DATA, objectMapper.toJSONB(data))
                .returning(PROCESS_EVENTS.EVENT_SEQ)
                .fetchOne();

        updateStats(tx, Collections.singletonList(new StatItem(startOfDay(processKey.getCreatedAt()), r.getEventSeq())));
    }

    public void insert(DSLContext tx, List<ProcessKey> processKeys, String eventType, Map<String, Object> data) {
        String sql = tx.insertInto(PROCESS_EVENTS)
                .set(PROCESS_EVENTS.INSTANCE_ID, (UUID) null)
                .set(PROCESS_EVENTS.INSTANCE_CREATED_AT, (Timestamp) null)
                .set(PROCESS_EVENTS.EVENT_TYPE, (String) null)
                .set(PROCESS_EVENTS.EVENT_DATE, currentTimestamp())
                .set(PROCESS_EVENTS.EVENT_DATA, (JSONB) null)
                .returning(PROCESS_EVENTS.EVENT_SEQ)
                .getSQL();

        tx.connection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (ProcessKey pk : processKeys) {
                    ps.setObject(1, pk.getInstanceId());
                    ps.setTimestamp(2, pk.getCreatedAt());
                    ps.setString(3, eventType);
                    ps.setString(4, objectMapper.toString(data));
                    ps.addBatch();
                }
                ps.executeBatch();

                updateStats(tx, toStatItems(ps, processKeys::get));
            }
        });
    }

    public void insertStatusHistory(DSLContext tx, ProcessKey processKey, ProcessStatus status, Map<String, Object> statusPayload) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status.name());
        payload.putAll(statusPayload);

        insert(tx, processKey, EventType.PROCESS_STATUS.name(), null, objectMapper.convertToMap(payload));
    }

    public void insertStatusHistory(DSLContext tx, List<ProcessKey> processKeys, ProcessStatus status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status.name());

        insert(tx, processKeys, EventType.PROCESS_STATUS.name(), objectMapper.convertToMap(payload));
    }

    private void insert(DSLContext tx, ProcessKey processKey, List<ProcessEventRequest> entries) {
        String sql = tx.insertInto(PROCESS_EVENTS)
                .set(PROCESS_EVENTS.INSTANCE_ID, (UUID) null)
                .set(PROCESS_EVENTS.INSTANCE_CREATED_AT, (Timestamp) null)
                .set(PROCESS_EVENTS.EVENT_TYPE, (String) null)
                .set(PROCESS_EVENTS.EVENT_DATE, (Timestamp) null)
                .set(PROCESS_EVENTS.EVENT_DATA, (JSONB) null)
                .returning(PROCESS_EVENTS.EVENT_SEQ)
                .getSQL();

        tx.connection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                for (ProcessEventRequest e : entries) {
                    Timestamp eventDate = e.getEventDate() != null ? Timestamp.from(e.getEventDate().toInstant()) : Timestamp.from(Instant.now());

                    ps.setObject(1, processKey.getInstanceId());
                    ps.setTimestamp(2, processKey.getCreatedAt());
                    ps.setString(3, e.getEventType());
                    ps.setTimestamp(4, eventDate);
                    ps.setString(5, objectMapper.toString(e.getData()));
                    ps.addBatch();
                }
                ps.executeBatch();

                updateStats(tx, toStatItems(ps, index -> processKey));
            }
        });
    }

    private static void updateStats(DSLContext tx, List<StatItem> items) {
        if (items.isEmpty()) {
            return;
        }

        ProcessEventStats p = PROCESS_EVENT_STATS.as("p");
        BatchBindStep q = tx.batch(
                tx.insertInto(p)
                        .columns(p.INSTANCE_CREATED_DATE, p.MAX_EVENT_SEQ)
                        .values((Timestamp) null, null)
                        .onDuplicateKeyUpdate()
                        .set(p.MAX_EVENT_SEQ, when(p.MAX_EVENT_SEQ.lessThan((Long)null), value((Long)null)).otherwise(p.MAX_EVENT_SEQ))
        );

        for (StatItem i : items) {
            q.bind(value(i.instanceCreatedDate), value(i.maxEventSeq), value(i.maxEventSeq), value(i.maxEventSeq), value(i.maxEventSeq));
        }

        q.execute();
    }

    private ProcessEventEntry toEntry(Record5<Long, UUID, String, Timestamp, JSONB> r) {
        return ImmutableProcessEventEntry.builder()
                .seqId(r.value1())
                .id(r.value2())
                .eventType(r.value3())
                .eventDate(r.value4())
                .data(objectMapper.fromJSONB(r.value5()))
                .build();
    }

    private static List<StatItem> toStatItems(PreparedStatement ps, Function<Integer, ProcessKey> processKey) throws SQLException {
        Map<Timestamp, Long> result = new HashMap<>();

        int index = 0;
        try (ResultSet rs = ps.getGeneratedKeys()) {
            while (rs.next()) {
                long eventSeq = rs.getLong(PROCESS_EVENTS.EVENT_SEQ.getName());
                Timestamp createdDate = startOfDay(processKey.apply(index).getCreatedAt());
                result.compute(createdDate, (k, v) -> (v == null) ? eventSeq : Math.max(eventSeq, v));
                index++;
            }
        }
        return result.entrySet().stream()
                .map(e -> new StatItem(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private static Timestamp startOfDay(Timestamp ts) {
        return Timestamp.valueOf(ts.toLocalDateTime().toLocalDate().atTime(LocalTime.MIN));
    }

    private static class StatItem {

        private final Timestamp instanceCreatedDate;

        private final long maxEventSeq;

        private StatItem(Timestamp instanceCreatedDate, long maxEventSeq) {
            this.instanceCreatedDate = instanceCreatedDate;
            this.maxEventSeq = maxEventSeq;
        }
    }
}
