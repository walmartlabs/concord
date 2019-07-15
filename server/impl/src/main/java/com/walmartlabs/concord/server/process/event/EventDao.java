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
import com.walmartlabs.concord.server.process.ProcessKey;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_EVENTS;
import static org.jooq.impl.DSL.*;

@Named
public class EventDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public EventDao(@MainDB Configuration cfg, ConcordObjectMapper objectMapper) {
        super(cfg);
        this.objectMapper = objectMapper;
    }

    public List<ProcessEventEntry> list(ProcessEventFilter filter) {
        try (DSLContext tx = DSL.using(cfg)) {

            ProcessKey processKey = filter.processKey();

            SelectConditionStep<Record4<UUID, String, Timestamp, String>> q = tx
                    .select(PROCESS_EVENTS.EVENT_ID,
                            PROCESS_EVENTS.EVENT_TYPE,
                            PROCESS_EVENTS.EVENT_DATE,
                            function("jsonb_strip_nulls", Object.class, PROCESS_EVENTS.EVENT_DATA).cast(String.class))
                    .from(PROCESS_EVENTS)
                    .where(PROCESS_EVENTS.INSTANCE_ID.eq(processKey.getInstanceId())
                            .and(PROCESS_EVENTS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())));

            Timestamp after = filter.after();
            if (after != null) {
                q.and(PROCESS_EVENTS.EVENT_DATE.ge(after));
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

    public void insert(ProcessKey processKey, String eventType, Map<String, Object> data) {
        tx(tx -> insert(tx, processKey, eventType, data));
    }

    public void insert(ProcessKey processKey, List<ProcessEventRequest> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        tx(tx -> {
            BatchBindStep b = tx.batch(tx.insertInto(PROCESS_EVENTS)
                    .columns(PROCESS_EVENTS.INSTANCE_ID,
                            PROCESS_EVENTS.INSTANCE_CREATED_AT,
                            PROCESS_EVENTS.EVENT_TYPE,
                            PROCESS_EVENTS.EVENT_DATE,
                            PROCESS_EVENTS.EVENT_DATA)
                    .values(null, null, null, currentTimestamp(), field("?::jsonb", "n/a")));

            for (ProcessEventRequest e : entries) {
                b.bind(processKey.getInstanceId(),
                        processKey.getCreatedAt(),
                        e.getEventType(),
                        objectMapper.serialize(e.getData()));
            }

            b.execute();
        });
    }

    public void insert(DSLContext tx, ProcessKey processKey, String eventType, Map<String, Object> data) {
        tx.insertInto(PROCESS_EVENTS)
                .columns(PROCESS_EVENTS.INSTANCE_ID,
                        PROCESS_EVENTS.INSTANCE_CREATED_AT,
                        PROCESS_EVENTS.EVENT_TYPE,
                        PROCESS_EVENTS.EVENT_DATE,
                        PROCESS_EVENTS.EVENT_DATA)
                .values(value(processKey.getInstanceId()),
                        value(processKey.getCreatedAt()),
                        value(eventType),
                        currentTimestamp(),
                        field("?::jsonb", objectMapper.serialize(data)))
                .execute();
    }

    public void insert(DSLContext tx, List<ProcessKey> processKeys, String eventType, Map<String, Object> data) {
        String sql = tx.insertInto(PROCESS_EVENTS,
                PROCESS_EVENTS.INSTANCE_ID,
                PROCESS_EVENTS.INSTANCE_CREATED_AT,
                PROCESS_EVENTS.EVENT_TYPE,
                PROCESS_EVENTS.EVENT_DATE,
                PROCESS_EVENTS.EVENT_DATA)
                .values(value((UUID) null), null, null, currentTimestamp(), field("?::jsonb"))
                .getSQL();

        tx.connection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (ProcessKey pk : processKeys) {
                    ps.setObject(1, pk.getInstanceId());
                    ps.setTimestamp(2, pk.getCreatedAt());
                    ps.setString(3, eventType);
                    ps.setString(4, objectMapper.serialize(data));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    private ProcessEventEntry toEntry(Record4<UUID, String, Timestamp, String> r) {
        return ImmutableProcessEventEntry.builder()
                .id(r.value1())
                .eventType(r.value2())
                .eventDate(r.value3())
                .data(objectMapper.deserialize(r.value4()))
                .build();
    }
}
