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
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.UuidGenerator;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessEventsRecord;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.events.ProcessEvent;
import org.jooq.*;

import javax.inject.Inject;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_EVENTS;
import static java.util.Objects.requireNonNull;
import static org.jooq.impl.DSL.*;

public class ProcessEventDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;

    @Inject
    public ProcessEventDao(@MainDB Configuration cfg,
                           ConcordObjectMapper objectMapper,
                           UuidGenerator uuidGenerator) {
        super(cfg);
        this.objectMapper = requireNonNull(objectMapper);
        this.uuidGenerator = requireNonNull(uuidGenerator);
    }

    @Override
    protected void tx(Tx t) {
        super.tx(t);
    }

    @Override
    protected <T> T txResult(TxResult<T> t) {
        return super.txResult(t);
    }

    public List<ProcessEventEntry> list(ProcessEventFilter filter) {
        ProcessKey processKey = filter.processKey();

        SelectConditionStep<Record5<Long, UUID, String, OffsetDateTime, JSONB>> q = dsl()
                .select(PROCESS_EVENTS.EVENT_SEQ,
                        PROCESS_EVENTS.EVENT_ID,
                        PROCESS_EVENTS.EVENT_TYPE,
                        PROCESS_EVENTS.EVENT_DATE,
                        function("jsonb_strip_nulls", JSONB.class, PROCESS_EVENTS.EVENT_DATA))
                .from(PROCESS_EVENTS)
                .where(PROCESS_EVENTS.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_EVENTS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())));

        OffsetDateTime after = filter.after();
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
            q.and(PgUtils.jsonbText(PROCESS_EVENTS.EVENT_DATA, "correlationId").eq(eventCorrelationId.toString()));
        }

        EventPhase eventPhase = filter.eventPhase();
        if (eventPhase != null) {
            q.and(PgUtils.jsonbText(PROCESS_EVENTS.EVENT_DATA, "phase").eq(eventPhase.getKey()));
        }

        int limit = filter.limit();
        if (limit > 0) {
            q.limit(limit);
        }

        int offset = filter.offset();
        if (offset > 0) {
            q.offset(offset);
        }

        return q.orderBy(PROCESS_EVENTS.EVENT_SEQ)
                .fetch(this::toEntry);
    }

    public void insert(DSLContext tx, List<ProcessKey> processKeys, String eventType, Map<String, Object> data) {

        String sql = tx.insertInto(PROCESS_EVENTS)
                .set(PROCESS_EVENTS.EVENT_ID, (UUID) null)
                .set(PROCESS_EVENTS.INSTANCE_ID, (UUID) null)
                .set(PROCESS_EVENTS.INSTANCE_CREATED_AT, (OffsetDateTime) null)
                .set(PROCESS_EVENTS.EVENT_TYPE, (String) null)
                .set(PROCESS_EVENTS.EVENT_DATE, currentOffsetDateTime())
                .set(PROCESS_EVENTS.EVENT_DATA, (JSONB) null)
                .returning(PROCESS_EVENTS.EVENT_SEQ)
                .getSQL();

        tx.connection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (ProcessKey pk : processKeys) {
                    UUID eventId = uuidGenerator.generate();
                    ps.setObject(1, eventId);
                    ps.setObject(2, pk.getInstanceId());
                    ps.setObject(3, pk.getCreatedAt());
                    ps.setString(4, eventType);
                    ps.setString(5, objectMapper.toJSONB(data).toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    /**
     * Batch inserts the provided {@code events}.
     *
     * @return the same events updated with autogenerated IDs
     */
    public List<ProcessEvent> insert(DSLContext tx, List<NewProcessEvent> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        if (events.size() == 1) {
            NewProcessEvent ev = events.get(0);

            UUID eventId = uuidGenerator.generate();
            ProcessKey processKey = ev.processKey();
            Field<OffsetDateTime> ts = ev.eventDate() != null ? value(ev.eventDate()) : currentOffsetDateTime();
            Map<String, Object> m = ev.data() != null ? ev.data() : Collections.emptyMap();
            String eventType = ev.eventType();

            ProcessEventsRecord r = tx.insertInto(PROCESS_EVENTS)
                    .set(PROCESS_EVENTS.EVENT_ID, eventId)
                    .set(PROCESS_EVENTS.INSTANCE_ID, processKey.getInstanceId())
                    .set(PROCESS_EVENTS.INSTANCE_CREATED_AT, processKey.getCreatedAt())
                    .set(PROCESS_EVENTS.EVENT_TYPE, eventType)
                    .set(PROCESS_EVENTS.EVENT_DATE, ts)
                    .set(PROCESS_EVENTS.EVENT_DATA, objectMapper.toJSONB(m))
                    .returning(PROCESS_EVENTS.EVENT_DATE, PROCESS_EVENTS.EVENT_SEQ)
                    .fetchOne();

            return Collections.singletonList(ProcessEvent.builder()
                    .eventSeq(r.getEventSeq())
                    .eventDate(r.getEventDate())
                    .eventType(eventType)
                    .processKey(processKey)
                    .data(m)
                    .build());
        }

        InsertSetStep<ProcessEventsRecord> q = tx.insertInto(PROCESS_EVENTS);
        InsertSetMoreStep<ProcessEventsRecord> qq = null;
        for (Iterator<NewProcessEvent> i = events.iterator(); i.hasNext(); ) {
            NewProcessEvent ev = i.next();

            ProcessKey pk = ev.processKey();

            ProcessEventsRecord r = new ProcessEventsRecord();
            r.setEventId(uuidGenerator.generate());
            r.setInstanceId(pk.getInstanceId());
            r.setInstanceCreatedAt(pk.getCreatedAt());
            r.setEventType(ev.eventType());

            // TODO replace with default = now()?
            OffsetDateTime eventDate = ev.eventDate() != null ? ev.eventDate() : OffsetDateTime.now();
            r.setEventDate(eventDate);

            Map<String, Object> m = ev.data() != null ? ev.data() : Collections.emptyMap();
            r.setEventData(objectMapper.toJSONB(m));

            qq = q.set(r);
            if (i.hasNext()) {
                qq.newRecord();
            }
        }

        // return only a subset of columns to save some traffic
        Result<ProcessEventsRecord> records = qq.returning(PROCESS_EVENTS.EVENT_DATE, PROCESS_EVENTS.EVENT_SEQ)
                .fetch();

        if (records.size() != events.size()) {
            throw new IllegalStateException("Invalid result. Returning records count doesn't match the number of events: " + records.size() + " != " + events.size());
        }

        List<ProcessEvent> result = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            ProcessEventsRecord r = records.get(i);
            NewProcessEvent ev = events.get(i);
            Map<String, Object> data = ev.data();

            result.add(ProcessEvent.builder()
                    .eventDate(r.getEventDate())
                    .eventSeq(r.getEventSeq())
                    .eventType(ev.eventType())
                    .processKey(ev.processKey())
                    .data(data != null ? data : Collections.emptyMap())
                    .build());
        }

        return result;
    }

    private ProcessEventEntry toEntry(Record5<Long, UUID, String, OffsetDateTime, JSONB> r) {
        return ImmutableProcessEventEntry.builder()
                .seqId(r.value1())
                .id(r.value2())
                .eventType(r.value3())
                .eventDate(r.value4())
                .data(objectMapper.fromJSONB(r.value5()))
                .build();
    }
}
