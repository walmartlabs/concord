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
import com.walmartlabs.concord.server.jooq.tables.records.ProcessLogDataRecord;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessLogSegmentsRecord;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessLogsRecord;
import com.walmartlabs.concord.server.process.LogSegment;
import com.walmartlabs.concord.server.process.ProcessKey;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.db.PgUtils.upperRange;
import static com.walmartlabs.concord.server.jooq.Routines.*;
import static com.walmartlabs.concord.server.jooq.Tables.*;
import static org.jooq.impl.DSL.*;

@Named
public class ProcessLogsDao extends AbstractDao {

    @Inject
    public ProcessLogsDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    /**
     * Appends a chunk to the process log. Automatically calculates the chunk's range.
     * @return the new chunk range.
     */
    public PgIntRange append(ProcessKey processKey, byte[] data) {
        UUID instanceId = processKey.getInstanceId();
        Timestamp createdAt = processKey.getCreatedAt();

        ProcessLogsRecord r = txResult(tx -> tx.insertInto(PROCESS_LOGS)
                .columns(PROCESS_LOGS.INSTANCE_ID,
                        PROCESS_LOGS.INSTANCE_CREATED_AT,
                        PROCESS_LOGS.CHUNK_RANGE,
                        PROCESS_LOGS.CHUNK_DATA)
                .values(value(instanceId),
                        value(createdAt),
                        processLogNextRange2(instanceId, createdAt, data.length),
                        value(data))
                .returning(PROCESS_LOGS.CHUNK_RANGE)
                .fetchOne());

        return PgIntRange.parse(r.getChunkRange().toString());
    }

    public PgIntRange append(ProcessKey processKey, long segmentId, byte[] data) {
        UUID instanceId = processKey.getInstanceId();
        Timestamp createdAt = processKey.getCreatedAt();

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
                        processLogDataSegmentNextRange(instanceId, createdAt, segmentId, data.length),
                        processLogDataNextRange(instanceId, createdAt, data.length),
                        value(data))
                .returning(PROCESS_LOG_DATA.LOG_RANGE)
                .fetchOne());

        return PgIntRange.parse(r.getLogRange().toString());
    }

    /**
     * @deprecated remove after process_logs is no longer in use
     */
    @Deprecated
    public ProcessLog get(ProcessKey processKey, Integer start, Integer end) {
        UUID instanceId = processKey.getInstanceId();
        Timestamp createdAt = processKey.getCreatedAt();

        try (DSLContext tx = DSL.using(cfg)) {
            List<ProcessLogChunk> chunks = getChunks(tx, processKey, start, end);

            int size = tx.select(V_PROCESS_LOGS_SIZE.SIZE)
                    .from(V_PROCESS_LOGS_SIZE)
                    .where(V_PROCESS_LOGS_SIZE.INSTANCE_ID.eq(instanceId)
                            .and(V_PROCESS_LOGS_SIZE.INSTANCE_CREATED_AT.eq(createdAt)))
                    .fetchOptional(V_PROCESS_LOGS_SIZE.SIZE)
                    .orElse(0);

            return new ProcessLog(size, chunks);
        }
    }

    public long createSegment(ProcessKey processKey, UUID correlationId, String name, Date createdAt, String status) {
        return txResult(tx -> tx.insertInto(PROCESS_LOG_SEGMENTS)
                .columns(PROCESS_LOG_SEGMENTS.INSTANCE_ID, PROCESS_LOG_SEGMENTS.INSTANCE_CREATED_AT, PROCESS_LOG_SEGMENTS.CORRELATION_ID, PROCESS_LOG_SEGMENTS.SEGMENT_NAME, PROCESS_LOG_SEGMENTS.SEGMENT_TS, PROCESS_LOG_SEGMENTS.SEGMENT_STATUS)
                .values(value(processKey.getInstanceId()), value(processKey.getCreatedAt()), value(correlationId), value(name), createdAt != null ? value(new Timestamp(createdAt.getTime())) : currentTimestamp(), value(status))
                .returning(PROCESS_LOG_SEGMENTS.SEGMENT_ID)
                .fetchOne()
                .getSegmentId());
    }

    public void createSegment(DSLContext tx, long segmentId, ProcessKey processKey, UUID correlationId, String name, String status) {
        tx.insertInto(PROCESS_LOG_SEGMENTS)
                .columns(PROCESS_LOG_SEGMENTS.SEGMENT_ID, PROCESS_LOG_SEGMENTS.INSTANCE_ID, PROCESS_LOG_SEGMENTS.INSTANCE_CREATED_AT, PROCESS_LOG_SEGMENTS.CORRELATION_ID, PROCESS_LOG_SEGMENTS.SEGMENT_NAME, PROCESS_LOG_SEGMENTS.SEGMENT_TS, PROCESS_LOG_SEGMENTS.SEGMENT_STATUS)
                .values(value(segmentId), value(processKey.getInstanceId()), value(processKey.getCreatedAt()), value(correlationId), value(name), currentTimestamp(), value(status))
                .execute();
    }

    public void updateSegment(ProcessKey processKey, long segmentId, LogSegment.Status status, Integer warnings, Integer errors) {
        tx(tx -> updateSegment(tx, processKey, segmentId, status, warnings, errors));
    }

    private void updateSegment(DSLContext tx, ProcessKey processKey, long segmentId, LogSegment.Status status, Integer warnings, Integer errors) {
        UpdateQuery<ProcessLogSegmentsRecord> q = tx.updateQuery(PROCESS_LOG_SEGMENTS);

        if (status != null) {
            q.addValue(PROCESS_LOG_SEGMENTS.SEGMENT_STATUS, status.name());
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

    public List<LogSegment> listSegments(ProcessKey processKey, int limit, int offset) {
        UUID instanceId = processKey.getInstanceId();
        Timestamp createdAt = processKey.getCreatedAt();

        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROCESS_LOG_SEGMENTS.SEGMENT_ID, PROCESS_LOG_SEGMENTS.CORRELATION_ID,
                    PROCESS_LOG_SEGMENTS.SEGMENT_NAME,
                    PROCESS_LOG_SEGMENTS.SEGMENT_TS,
                    PROCESS_LOG_SEGMENTS.SEGMENT_STATUS,
                    PROCESS_LOG_SEGMENTS.SEGMENT_WARN,
                    PROCESS_LOG_SEGMENTS.SEGMENT_ERRORS)
                    .from(PROCESS_LOG_SEGMENTS)
                    .where(PROCESS_LOG_SEGMENTS.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_LOG_SEGMENTS.INSTANCE_CREATED_AT.eq(createdAt)))
                    .orderBy(PROCESS_LOG_SEGMENTS.SEGMENT_TS)
                    .limit(limit)
                    .offset(offset)
                    .fetch(ProcessLogsDao::toSegment);
        }
    }

    public ProcessLog segmentData(ProcessKey processKey, long segmentId, Integer start, Integer end) {
        UUID instanceId = processKey.getInstanceId();
        Timestamp createdAt = processKey.getCreatedAt();

        try (DSLContext tx = DSL.using(cfg)) {
            List<ProcessLogChunk> chunks = getSegmentChunks(tx, processKey, segmentId, start, end);

            Field<Integer> upperRange = max(upperRange(PROCESS_LOG_DATA.SEGMENT_RANGE));
            int size = tx.select(upperRange)
                    .from(PROCESS_LOG_DATA)
                    .where(PROCESS_LOG_DATA.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_LOG_DATA.INSTANCE_CREATED_AT.eq(createdAt))
                            .and(PROCESS_LOG_DATA.SEGMENT_ID.eq(segmentId)))
                    .fetchOptional(upperRange)
                    .orElse(0);

            return new ProcessLogsDao.ProcessLog(size, chunks);
        }
    }

    public ProcessLog data(ProcessKey processKey, Integer start, Integer end) {
        UUID instanceId = processKey.getInstanceId();
        Timestamp createdAt = processKey.getCreatedAt();

        try (DSLContext tx = DSL.using(cfg)) {
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
    }

    private List<ProcessLogChunk> getChunks(DSLContext tx, ProcessKey processKey, Integer start, Integer end) {
        UUID instanceId = processKey.getInstanceId();
        Timestamp createdAt = processKey.getCreatedAt();

        String lowerBoundExpr = "lower(" + PROCESS_LOGS.CHUNK_RANGE + ")";

        if (start == null && end == null) {
            // entire file
            return tx.select(field(lowerBoundExpr), PROCESS_LOGS.CHUNK_DATA)
                    .from(PROCESS_LOGS)
                    .where(PROCESS_LOGS.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_LOGS.INSTANCE_CREATED_AT.eq(createdAt)))
                    .orderBy(PROCESS_LOGS.CHUNK_RANGE)
                    .fetch(ProcessLogsDao::toChunk);

        } else if (start != null) {
            // ranges && [start, end)
            String rangeExpr = PROCESS_LOGS.CHUNK_RANGE.getName() + " && int4range(?, ?)";
            return tx.select(field(lowerBoundExpr), PROCESS_LOGS.CHUNK_DATA)
                    .from(PROCESS_LOGS)
                    .where(PROCESS_LOGS.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_LOGS.INSTANCE_CREATED_AT.eq(createdAt))
                            .and(rangeExpr, start, end))
                    .orderBy(PROCESS_LOGS.CHUNK_RANGE)
                    .fetch(ProcessLogsDao::toChunk);

        } else {
            // ranges && [upper_bound - end, upper_bound)
            String rangeExpr = PROCESS_LOGS.CHUNK_RANGE.getName() + " && (select range from x)";
            return tx.with("x").as(select(processLogLastNBytes2(instanceId, createdAt, end).as("range")))
                    .select(field(lowerBoundExpr), PROCESS_LOGS.CHUNK_DATA)
                    .from(PROCESS_LOGS)
                    .where(PROCESS_LOGS.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_LOGS.INSTANCE_CREATED_AT.eq(createdAt))
                            .and(rangeExpr, instanceId, end))
                    .orderBy(PROCESS_LOGS.CHUNK_RANGE)
                    .fetch(ProcessLogsDao::toChunk);
        }
    }

    private List<ProcessLogChunk> getSegmentChunks(DSLContext tx, ProcessKey processKey, long segmentId, Integer start, Integer end) {
        UUID instanceId = processKey.getInstanceId();
        Timestamp createdAt = processKey.getCreatedAt();

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
            return tx.with("x").as(select(processLogDataSegmentLastNBytes(instanceId, createdAt, segmentId, end).as("range")))
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
        Timestamp createdAt = processKey.getCreatedAt();

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
            return tx.with("x").as(select(processLogDataLastNBytes(instanceId, createdAt, end).as("range")))
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

    private static LogSegment toSegment(Record7<Long, UUID, String, Timestamp, String, Integer, Integer> r) {
        String status = r.get(PROCESS_LOG_SEGMENTS.SEGMENT_STATUS);
        return LogSegment.builder()
                .id(r.get(PROCESS_LOG_SEGMENTS.SEGMENT_ID))
                .correlationId(r.get(PROCESS_LOG_SEGMENTS.CORRELATION_ID))
                .name(r.get(PROCESS_LOG_SEGMENTS.SEGMENT_NAME))
                .createdAt(r.get(PROCESS_LOG_SEGMENTS.SEGMENT_TS))
                .warnings(r.get(PROCESS_LOG_SEGMENTS.SEGMENT_WARN))
                .errors(r.get(PROCESS_LOG_SEGMENTS.SEGMENT_ERRORS))
                .status(status != null ? LogSegment.Status.valueOf(status) : null)
                .build();
    }

    public static final class ProcessLogChunk implements Serializable {

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
