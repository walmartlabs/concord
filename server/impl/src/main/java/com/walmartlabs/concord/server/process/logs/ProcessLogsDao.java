package com.walmartlabs.concord.server.process.logs;

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
import com.walmartlabs.concord.db.PgIntRange;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.jooq.tables.NamedProcessLogSegments;
import com.walmartlabs.concord.server.jooq.tables.ProcessLogSegments;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessLogDataRecord;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessLogSegmentsRecord;
import com.walmartlabs.concord.server.process.LogSegment;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.Range;
import org.jooq.*;

import javax.inject.Inject;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.db.PgUtils.jsonbText;
import static com.walmartlabs.concord.db.PgUtils.upperRange;
import static com.walmartlabs.concord.server.jooq.Routines.*;
import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static org.jooq.impl.DSL.*;

public class ProcessLogsDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public ProcessLogsDao(@MainDB Configuration cfg, ConcordObjectMapper objectMapper) {
        super(cfg);
        this.objectMapper = objectMapper;
    }

    /**
     * Appends a chunk to the process log. Automatically calculates the chunk's range.
     *
     * @return the new chunk range.
     */
    public Range append(ProcessKey processKey, long segmentId, byte[] data) {
        UUID instanceId = processKey.getInstanceId();
        OffsetDateTime createdAt = processKey.getCreatedAt();

        ProcessLogDataRecord r = txResult(tx -> tx.insertInto(PROCESS_LOG_DATA)
                .columns(PROCESS_LOG_DATA.INSTANCE_ID,
                        PROCESS_LOG_DATA.INSTANCE_CREATED_AT,
                        PROCESS_LOG_DATA.SEGMENT_ID,
                        PROCESS_LOG_DATA.SEGMENT_RANGE,
                        PROCESS_LOG_DATA.LOG_RANGE,
                        PROCESS_LOG_DATA.CHUNK_DATA)
                .values(value(instanceId),
                        value(createdAt),
                        value(segmentId),
                        processLogDataSegmentNextRange2(instanceId, createdAt, segmentId, data.length),
                        processLogDataNextRange2(instanceId, createdAt, data.length),
                        value(data))
                .returning(PROCESS_LOG_DATA.LOG_RANGE)
                .fetchOne());

        return PgIntRange.parse(r.getLogRange().toString());
    }

    public long createSegment(ProcessKey processKey, UUID correlationId, String name, OffsetDateTime createdAt, String status, Long parentId, Map<String, Object> meta) {
        return txResult(tx -> tx.insertInto(PROCESS_LOG_SEGMENTS)
                .columns(PROCESS_LOG_SEGMENTS.INSTANCE_ID,
                        PROCESS_LOG_SEGMENTS.INSTANCE_CREATED_AT,
                        PROCESS_LOG_SEGMENTS.CORRELATION_ID,
                        PROCESS_LOG_SEGMENTS.SEGMENT_NAME,
                        PROCESS_LOG_SEGMENTS.SEGMENT_TS,
                        PROCESS_LOG_SEGMENTS.SEGMENT_STATUS,
                        PROCESS_LOG_SEGMENTS.PARENT_SEGMENT_ID,
                        PROCESS_LOG_SEGMENTS.META)
                .values(value(processKey.getInstanceId()),
                        value(processKey.getCreatedAt()),
                        value(correlationId), value(name),
                        createdAt != null ? value(createdAt) : currentOffsetDateTime(),
                        value(status),
                        value(parentId),
                        value(objectMapper.toJSONB(meta)))
                .returning(PROCESS_LOG_SEGMENTS.SEGMENT_ID)
                .fetchOne()
                .getSegmentId());
    }

    public void createSegment(DSLContext tx, long segmentId, ProcessKey processKey, UUID correlationId, String name, String status, Long parentId, Map<String, Object> meta) {
        tx.insertInto(PROCESS_LOG_SEGMENTS)
                .columns(PROCESS_LOG_SEGMENTS.SEGMENT_ID, PROCESS_LOG_SEGMENTS.INSTANCE_ID, PROCESS_LOG_SEGMENTS.INSTANCE_CREATED_AT, PROCESS_LOG_SEGMENTS.CORRELATION_ID, PROCESS_LOG_SEGMENTS.SEGMENT_NAME, PROCESS_LOG_SEGMENTS.SEGMENT_TS, PROCESS_LOG_SEGMENTS.SEGMENT_STATUS, PROCESS_LOG_SEGMENTS.PARENT_SEGMENT_ID, PROCESS_LOG_SEGMENTS.META)
                .values(value(segmentId), value(processKey.getInstanceId()), value(processKey.getCreatedAt()), value(correlationId), value(name), currentOffsetDateTime(), value(status), value(parentId), value(objectMapper.toJSONB(meta)))
                .execute();
    }

    public void updateSegment(ProcessKey processKey, long segmentId, LogSegment.Status status, Integer warnings, Integer errors) {
        tx(tx -> updateSegment(tx, processKey, segmentId, status, warnings, errors));
    }

    private void updateSegment(DSLContext tx, ProcessKey processKey, long segmentId, LogSegment.Status status, Integer warnings, Integer errors) {
        UpdateQuery<ProcessLogSegmentsRecord> q = tx.updateQuery(PROCESS_LOG_SEGMENTS);

        if (status != null) {
            q.addValue(PROCESS_LOG_SEGMENTS.SEGMENT_STATUS, status.name());
            q.addValue(PROCESS_LOG_SEGMENTS.STATUS_UPDATED_AT, currentOffsetDateTime());
        }

        if (warnings != null) {
            q.addValue(PROCESS_LOG_SEGMENTS.SEGMENT_WARN, warnings);
        }

        if (errors != null) {
            q.addValue(PROCESS_LOG_SEGMENTS.SEGMENT_ERRORS, errors);
        }

        q.addConditions(
                PROCESS_LOG_SEGMENTS.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_LOG_SEGMENTS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())
                                .and(PROCESS_LOG_SEGMENTS.SEGMENT_ID.eq(segmentId))));
        q.execute();
    }

    public List<LogSegment> listSegments(ProcessKey processKey, int limit, int offset,
                                         Long parentId, boolean rootSegments, boolean collectErrors,
                                         boolean onlyNamedSegments) {
        UUID instanceId = processKey.getInstanceId();
        OffsetDateTime createdAt = processKey.getCreatedAt();

        ProcessLogSegments pls = PROCESS_LOG_SEGMENTS.as("pls");

        Field<BigDecimal> errorsSumField = field(value(BigDecimal.ZERO));
        if (collectErrors) {
            ProcessLogSegments t = PROCESS_LOG_SEGMENTS.as("t");

            errorsSumField = dsl().withRecursive("parents").as(
                            select(PROCESS_LOG_SEGMENTS.INSTANCE_ID, PROCESS_LOG_SEGMENTS.INSTANCE_CREATED_AT, PROCESS_LOG_SEGMENTS.SEGMENT_ID, PROCESS_LOG_SEGMENTS.PARENT_SEGMENT_ID, PROCESS_LOG_SEGMENTS.SEGMENT_ERRORS)
                                    .from(PROCESS_LOG_SEGMENTS)
                                    .where(PROCESS_LOG_SEGMENTS.INSTANCE_ID.eq(pls.INSTANCE_ID)
                                            .and(PROCESS_LOG_SEGMENTS.INSTANCE_CREATED_AT.eq(pls.INSTANCE_CREATED_AT))
                                            .and(PROCESS_LOG_SEGMENTS.SEGMENT_ID.eq(pls.SEGMENT_ID)))
                                    .unionAll(
                                            select(t.INSTANCE_ID, t.INSTANCE_CREATED_AT, t.SEGMENT_ID, t.PARENT_SEGMENT_ID, t.SEGMENT_ERRORS)
                                                    .from(t)
                                                    .innerJoin(name("parents"))
                                                    .on(t.PARENT_SEGMENT_ID.eq(field(name("parents", "SEGMENT_ID"), Long.class)))
                                                    .and(t.INSTANCE_ID.eq(pls.INSTANCE_ID))
                                                    .and(t.INSTANCE_CREATED_AT.eq(pls.INSTANCE_CREATED_AT))
                                    ))
                    .select(sum(field("parents.SEGMENT_ERRORS", Integer.class)))
                    .from(name("parents")).asField();
        }

        SelectConditionStep<Record10<Long, UUID, String, OffsetDateTime, String, OffsetDateTime, Integer, Integer, JSONB, BigDecimal>> q = null;

        if (onlyNamedSegments) {
            NamedProcessLogSegments npls = NAMED_PROCESS_LOG_SEGMENTS.as("pls");
            q = dsl()
                    .select(npls.SEGMENT_ID,
                            npls.CORRELATION_ID,
                            npls.SEGMENT_NAME,
                            npls.SEGMENT_TS,
                            npls.SEGMENT_STATUS,
                            npls.STATUS_UPDATED_AT,
                            npls.SEGMENT_WARN,
                            npls.SEGMENT_ERRORS,
                            npls.META,
                            errorsSumField.as("totalErrors"))
                    .from(namedProcessLogSegments(instanceId, createdAt, parentId).as("pls"))
                    .where(npls.SEGMENT_ID.eq(npls.SEGMENT_ID));

        } else {
            q = dsl()
                    .select(pls.SEGMENT_ID,
                            pls.CORRELATION_ID,
                            pls.SEGMENT_NAME,
                            pls.SEGMENT_TS,
                            pls.SEGMENT_STATUS,
                            pls.STATUS_UPDATED_AT,
                            pls.SEGMENT_WARN,
                            pls.SEGMENT_ERRORS,
                            pls.META,
                            errorsSumField.as("totalErrors"))
                    .from(pls)
                    .where(pls.INSTANCE_ID.eq(instanceId)
                            .and(pls.INSTANCE_CREATED_AT.eq(createdAt)));

            if (parentId != null) {
                q.and(and(pls.PARENT_SEGMENT_ID.eq(parentId)));
            }

            if (rootSegments) {
                q.and(and(pls.PARENT_SEGMENT_ID.isNull()));
            }
        }

        q.orderBy(pls.SEGMENT_TS, pls.SEGMENT_ID);

        if (limit >= 0) {
            q.limit(limit);
        }

        return q.offset(offset)
                .fetch(this::toSegment);
    }

    public ProcessLog segmentData(ProcessKey processKey, long segmentId, Integer start, Integer end) {
        UUID instanceId = processKey.getInstanceId();
        OffsetDateTime createdAt = processKey.getCreatedAt();

        DSLContext tx = dsl();

        List<ProcessLogChunk> chunks = getSegmentChunks(tx, processKey, segmentId, start, end);

        Field<Integer> upperRange = max(upperRange(PROCESS_LOG_DATA.SEGMENT_RANGE));
        int size = tx.select(upperRange)
                .from(PROCESS_LOG_DATA)
                .where(PROCESS_LOG_DATA.INSTANCE_ID.eq(instanceId)
                        .and(PROCESS_LOG_DATA.INSTANCE_CREATED_AT.eq(createdAt))
                        .and(PROCESS_LOG_DATA.SEGMENT_ID.eq(segmentId)))
                .fetchOptional(upperRange)
                .orElse(0);

        return new ProcessLog(size, chunks);
    }

    public ProcessLog data(ProcessKey processKey, Integer start, Integer end) {
        UUID instanceId = processKey.getInstanceId();
        OffsetDateTime createdAt = processKey.getCreatedAt();

        DSLContext tx = dsl();

        List<ProcessLogChunk> chunks = getDataChunks(tx, processKey, start, end);

        Field<Integer> upperRange = max(upperRange(PROCESS_LOG_DATA.LOG_RANGE));
        int size = tx.select(upperRange)
                .from(PROCESS_LOG_DATA)
                .where(PROCESS_LOG_DATA.INSTANCE_ID.eq(instanceId)
                        .and(PROCESS_LOG_DATA.INSTANCE_CREATED_AT.eq(createdAt)))
                .fetchOptional(upperRange)
                .orElse(0);

        return new ProcessLog(size, chunks);
    }

    private List<ProcessLogChunk> getSegmentChunks(DSLContext tx, ProcessKey processKey, long segmentId, Integer start, Integer end) {
        UUID instanceId = processKey.getInstanceId();
        OffsetDateTime createdAt = processKey.getCreatedAt();

        String lowerBoundExpr = "lower(" + PROCESS_LOG_DATA.SEGMENT_RANGE + ")";

        if (start == null && end == null) {
            // entire file
            return tx.select(field(lowerBoundExpr), PROCESS_LOG_DATA.CHUNK_DATA)
                    .from(PROCESS_LOG_DATA)
                    .where(PROCESS_LOG_DATA.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_LOG_DATA.INSTANCE_CREATED_AT.eq(createdAt))
                            .and(PROCESS_LOG_DATA.SEGMENT_ID.eq(segmentId)))
                    .orderBy(PROCESS_LOG_DATA.SEGMENT_RANGE)
                    .fetch(ProcessLogsDao::toChunk);

        } else if (start != null) {
            // ranges && [start, end)
            String rangeExpr = PROCESS_LOG_DATA.SEGMENT_RANGE.getName() + " && int4range(?, ?)";
            return tx.select(field(lowerBoundExpr), PROCESS_LOG_DATA.CHUNK_DATA)
                    .from(PROCESS_LOG_DATA)
                    .where(PROCESS_LOG_DATA.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_LOG_DATA.INSTANCE_CREATED_AT.eq(createdAt))
                            .and(PROCESS_LOG_DATA.SEGMENT_ID.eq(segmentId))
                            .and(rangeExpr, start, end))
                    .orderBy(PROCESS_LOG_DATA.SEGMENT_RANGE)
                    .fetch(ProcessLogsDao::toChunk);

        } else {
            // ranges && [upper_bound - end, upper_bound)
            String rangeExpr = PROCESS_LOG_DATA.SEGMENT_RANGE.getName() + " && (select range from x)";
            return tx.with("x").as(select(processLogDataSegmentLastNBytes2(instanceId, createdAt, segmentId, end).as("range")))
                    .select(field(lowerBoundExpr), PROCESS_LOG_DATA.CHUNK_DATA)
                    .from(PROCESS_LOG_DATA)
                    .where(PROCESS_LOG_DATA.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_LOG_DATA.INSTANCE_CREATED_AT.eq(createdAt))
                            .and(PROCESS_LOG_DATA.SEGMENT_ID.eq(segmentId))
                            .and(rangeExpr, instanceId, end))
                    .orderBy(PROCESS_LOG_DATA.SEGMENT_RANGE)
                    .fetch(ProcessLogsDao::toChunk);
        }
    }

    private List<ProcessLogChunk> getDataChunks(DSLContext tx, ProcessKey processKey, Integer start, Integer end) {
        UUID instanceId = processKey.getInstanceId();
        OffsetDateTime createdAt = processKey.getCreatedAt();

        String lowerBoundExpr = "lower(" + PROCESS_LOG_DATA.LOG_RANGE + ")";

        if (start == null && end == null) {
            // entire file
            return tx.select(field(lowerBoundExpr), PROCESS_LOG_DATA.CHUNK_DATA)
                    .from(PROCESS_LOG_DATA)
                    .where(PROCESS_LOG_DATA.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_LOG_DATA.INSTANCE_CREATED_AT.eq(createdAt)))
                    .orderBy(PROCESS_LOG_DATA.LOG_RANGE)
                    .fetch(ProcessLogsDao::toChunk);

        } else if (start != null) {
            // ranges && [start, end)
            String rangeExpr = PROCESS_LOG_DATA.LOG_RANGE.getName() + " && int4range(?, ?)";
            return tx.select(field(lowerBoundExpr), PROCESS_LOG_DATA.CHUNK_DATA)
                    .from(PROCESS_LOG_DATA)
                    .where(PROCESS_LOG_DATA.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_LOG_DATA.INSTANCE_CREATED_AT.eq(createdAt))
                            .and(rangeExpr, start, end))
                    .orderBy(PROCESS_LOG_DATA.LOG_RANGE)
                    .fetch(ProcessLogsDao::toChunk);

        } else {
            // ranges && [upper_bound - end, upper_bound)
            String rangeExpr = PROCESS_LOG_DATA.LOG_RANGE.getName() + " && (select range from x)";
            return tx.with("x").as(select(processLogDataLastNBytes2(instanceId, createdAt, end).as("range")))
                    .select(field(lowerBoundExpr), PROCESS_LOG_DATA.CHUNK_DATA)
                    .from(PROCESS_LOG_DATA)
                    .where(PROCESS_LOG_DATA.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_LOG_DATA.INSTANCE_CREATED_AT.eq(createdAt))
                            .and(rangeExpr, instanceId, end))
                    .orderBy(PROCESS_LOG_DATA.LOG_RANGE)
                    .fetch(ProcessLogsDao::toChunk);
        }
    }

    private static ProcessLogChunk toChunk(Record2<Object, byte[]> r) {
        return new ProcessLogChunk((Integer) r.value1(), r.value2());
    }

    private LogSegment toSegment(Record10<Long, UUID, String, OffsetDateTime, String, OffsetDateTime, Integer, Integer, JSONB, BigDecimal> r) {
        String status = r.get(PROCESS_LOG_SEGMENTS.SEGMENT_STATUS);
        Map<String, Object> meta = objectMapper.fromJSONB(r.get(PROCESS_LOG_SEGMENTS.META));
        if (meta == null) {
            meta = Collections.emptyMap();
        }
        BigDecimal totalErrors = r.get("totalErrors", BigDecimal.class);
        return LogSegment.builder()
                .id(r.get(PROCESS_LOG_SEGMENTS.SEGMENT_ID))
                .correlationId(r.get(PROCESS_LOG_SEGMENTS.CORRELATION_ID))
                .name(r.get(PROCESS_LOG_SEGMENTS.SEGMENT_NAME))
                .createdAt(r.get(PROCESS_LOG_SEGMENTS.SEGMENT_TS))
                .status(status != null ? LogSegment.Status.valueOf(status) : null)
                .statusUpdatedAt(r.get(PROCESS_LOG_SEGMENTS.STATUS_UPDATED_AT))
                .warnings(r.get(PROCESS_LOG_SEGMENTS.SEGMENT_WARN))
                .errors(r.get(PROCESS_LOG_SEGMENTS.SEGMENT_ERRORS))
                .childHasErrors(totalErrors != null && totalErrors.longValue() > 0)
                .meta(meta)
                .build();
    }

    public static final class ProcessLogChunk implements Serializable {

        private static final long serialVersionUID = 1L;

        private final int start;
        private final byte[] data;

        public ProcessLogChunk(int start, byte[] data) { // NOSONAR
            this.start = start;
            this.data = data;
        }

        public int getStart() {
            return start;
        }

        public byte[] getData() {
            return data;
        }
    }

    public static final class ProcessLog implements Serializable {

        private static final long serialVersionUID = 1L;

        private final int size;
        private final List<ProcessLogChunk> chunks;

        public ProcessLog(int size, List<ProcessLogChunk> chunks) {
            this.size = size;
            this.chunks = chunks;
        }

        public int getSize() {
            return size;
        }

        public List<ProcessLogChunk> getChunks() {
            return chunks;
        }
    }
}
