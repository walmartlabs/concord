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

import com.walmartlabs.concord.server.process.logs.ProcessLogsDao;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProcessLogDataTest {
    static void schema(DSLContext dsl) {
        dsl.execute("""
                CREATE TABLE process_log_data (
                    instance_id uuid NOT NULL, instance_created_at timestamptz NOT NULL,
                    segment_id bigint NOT NULL, log_range int4range NOT NULL,
                    segment_range int4range NOT NULL, chunk_data bytea NOT NULL,
                    log_seq bigserial NOT NULL)
                """);
        dsl.execute("CREATE EXTENSION IF NOT EXISTS btree_gist");
        for (var range : new String[]{"log", "segment"}) {
            var scopeColumns = "instance_id, instance_created_at, " + (range.equals("segment") ? "segment_id, " : "");
            dsl.execute("CREATE INDEX idx_pld_" + range + "_upper ON process_log_data("
                    + scopeColumns + "(upper(" + range + "_range)))");
            dsl.execute("CREATE INDEX idx_pld_" + range + "_overlap ON process_log_data USING gist("
                    + scopeColumns + range + "_range)");
            var parameters = "p_instance_id uuid, p_created_at timestamptz, "
                    + (range.equals("segment") ? "p_segment_id bigint, " : "") + "p_data_len int";
            var scope = "instance_id=p_instance_id AND instance_created_at=p_created_at"
                    + (range.equals("segment") ? " AND segment_id=p_segment_id" : "");
            for (var operation : new String[]{"next_range", "last_n_bytes"}) {
                var function = "process_log_data_" + (range.equals("segment") ? "segment_" : "") + operation;
                var result = operation.equals("next_range") ? "int4range(n,n+p_data_len)" : "int4range(n-p_data_len,n)";
                dsl.execute("CREATE FUNCTION " + function + "(" + parameters + ") RETURNS int4range LANGUAGE plpgsql AS $$ "
                        + "DECLARE n int; BEGIN SELECT coalesce(max(upper(" + range + "_range)),0) INTO n FROM process_log_data WHERE "
                        + scope + "; RETURN " + result + "; END; $$");
            }
        }
    }

    @Test
    void readsBoundedRangesAndAppendsAfterLargeHistory() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:14.21-alpine")) {
            db.start();
            try (var conn = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword())) {
                var dsl = DSL.using(conn, SQLDialect.POSTGRES);
                schema(dsl);
                var key = new ProcessKey(UUID.randomUUID(), OffsetDateTime.parse("2026-10-19T00:00:00Z"));
                dsl.execute("""
                        INSERT INTO process_log_data(instance_id, instance_created_at, segment_id, log_range, segment_range, chunk_data)
                        SELECT ?::uuid, ?::timestamptz, 1, int4range(n*4,n*4+4), int4range(n*4,n*4+4), decode('41424344','hex')
                        FROM generate_series(0,99999) n
                        """, key.getInstanceId(), key.getCreatedAt());
                dsl.execute("ANALYZE process_log_data");
                var dao = new ProcessLogsDao(dsl.configuration());
                var page = dao.segmentData(key, 1, 399989, 399995);
                assertEquals(400000, page.getSize());
                assertEquals(2, page.getChunks().size());
                assertEquals(399988, page.getChunks().getFirst().getStart());
                assertEquals(399992, page.getChunks().getLast().getStart());
                assertTrue(dao.segmentData(key, 1, 400000, 400001).getChunks().isEmpty());
                assertTrue(dao.segmentData(new ProcessKey(key.getInstanceId(), key.getCreatedAt().plusDays(1)), 1, 0, 4).getChunks().isEmpty());
                assertEquals(399996, dao.data(key, null, 1).getChunks().getFirst().getStart());
                dao.append(key, 2, new byte[]{65, 66});
                assertEquals(400002, dao.data(key, 400000, null).getSize());
                assertEquals(0, dao.segmentData(key, 2, 0, null).getChunks().getFirst().getStart());
                assertEquals(2, dao.segmentData(key, 2, null, null).getSize());
                assertEquals(400000, dao.segmentData(key, 1, null, 4).getSize());
                // Historical concurrent writers could leave overlapping blocks.
                // Preserve native range-overlap semantics rather than assuming a
                // predecessor block is the only possible intersection.
                var damaged = new ProcessKey(UUID.randomUUID(), key.getCreatedAt());
                dsl.execute("""
                        INSERT INTO process_log_data(instance_id,instance_created_at,segment_id,log_range,segment_range,chunk_data)
                        SELECT ?::uuid,?::timestamptz,1,r,r,decode('41','hex')
                        FROM unnest(ARRAY[int4range(0,100),int4range(0,1),int4range(1,2)]) r
                        """, damaged.getInstanceId(), damaged.getCreatedAt());
                assertEquals(1, dao.segmentData(damaged, 1, 50, 51).getChunks().size());
                assertEquals(1, dao.data(damaged, 50, 51).getChunks().size());
                assertEquals(0, dao.segmentData(damaged, 1, 50, 51).getChunks().getFirst().getStart());
                assertTrue(dao.segmentData(damaged, 1, 50, 50).getChunks().isEmpty());
                assertTrue(dao.data(damaged, 50, 50).getChunks().isEmpty());
            }
        }
    }
}
