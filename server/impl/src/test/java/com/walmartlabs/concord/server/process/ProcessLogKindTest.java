package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.server.jooq.enums.LogSegmentKind;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_LOG_SEGMENTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProcessLogKindTest {

    @Test
    void storesEnumKindsAndReadsThemBack() {
        var stored = new AtomicReference<LogSegmentKind>();
        var now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        var dsl = DSL.using(SQLDialect.POSTGRES);
        var connection = new MockConnection(ctx -> {
            if (ctx.sql().startsWith("insert")) {
                stored.set(LogSegmentKind.valueOf(ctx.bindings()[7].toString()));
            }
            var fields = ctx.sql().startsWith("insert")
                    ? new org.jooq.Field<?>[] { PROCESS_LOG_SEGMENTS.SEGMENT_ID }
                    : new org.jooq.Field<?>[] {
                        PROCESS_LOG_SEGMENTS.SEGMENT_ID, PROCESS_LOG_SEGMENTS.CORRELATION_ID,
                        PROCESS_LOG_SEGMENTS.SEGMENT_NAME, PROCESS_LOG_SEGMENTS.SEGMENT_TS,
                        PROCESS_LOG_SEGMENTS.SEGMENT_STATUS, PROCESS_LOG_SEGMENTS.STATUS_UPDATED_AT,
                        PROCESS_LOG_SEGMENTS.SEGMENT_WARN, PROCESS_LOG_SEGMENTS.SEGMENT_ERRORS,
                        PROCESS_LOG_SEGMENTS.PARENT_SEGMENT_ID, PROCESS_LOG_SEGMENTS.SEGMENT_KIND,
                        PROCESS_LOG_SEGMENTS.SEGMENT_ITERATION_INDEX, PROCESS_LOG_SEGMENTS.SEGMENT_ATTEMPT_INDEX, PROCESS_LOG_SEGMENTS.SEGMENT_THREAD };
            var row = dsl.newRecord(fields);
            row.set(PROCESS_LOG_SEGMENTS.SEGMENT_ID, 1L);
            if (!ctx.sql().startsWith("insert")) {
                row.set(PROCESS_LOG_SEGMENTS.SEGMENT_NAME, "example");
                row.set(PROCESS_LOG_SEGMENTS.SEGMENT_TS, now);
                row.set(PROCESS_LOG_SEGMENTS.SEGMENT_KIND, stored.get());
                row.set(PROCESS_LOG_SEGMENTS.SEGMENT_ITERATION_INDEX, 3);
                row.set(PROCESS_LOG_SEGMENTS.SEGMENT_ATTEMPT_INDEX, 2);
            }
            var result = dsl.newResult(fields);
            result.add(row);
            return new MockResult[] { new MockResult(1, result) };
        });
        var dao = new ProcessLogsDao(DSL.using(connection, SQLDialect.POSTGRES).configuration());
        var key = new ProcessKey(UUID.randomUUID(), now);
        var kinds = LogSegment.Kind.values();
        for (var i = 0; i < kinds.length; i++) {
            dao.createSegment(key, ImmutableLogSegmentRequest.builder()
                    .name("example").createdAt(now).kind(kinds[i]).iterationIndex(3).attemptIndex(2).build(), "RUNNING");
            assertEquals(kinds[i].name(), stored.get().getLiteral());
            var result = dao.listSegments(key, -1, 0).getFirst();
            assertEquals(kinds[i], result.kind());
            assertEquals(3, result.iterationIndex());
            assertEquals(2, result.attemptIndex());
        }
        stored.set(null);
        assertNull(dao.listSegments(key, -1, 0).getFirst().kind());
    }
}
