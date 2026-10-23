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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.process.logs.ProcessLogTreeDao;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.jooq.SQLDialect;
import org.jooq.ExecuteContext;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultExecuteListener;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProcessLogTreeProbeTest {
    @Test
    void leafAnnotationsUseBoundedIndexedProbesOnSkewedParents() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:14.21-alpine")) {
            db.start();
            try (var conn = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword())) {
                var dsl = DSL.using(conn, SQLDialect.POSTGRES);
                dsl.execute("CREATE TYPE log_segment_kind AS ENUM ('STEP', 'ITERATION', 'ATTEMPT', 'ERROR', 'PARALLEL')");
                dsl.execute("""
                        CREATE TABLE process_log_segments (
                          instance_id uuid, instance_created_at timestamptz, segment_id bigint,
                          parent_segment_id bigint, segment_name text, segment_kind log_segment_kind,
                          segment_iteration_index int, segment_attempt_index int, segment_thread int,
                          correlation_id uuid, segment_ts timestamptz DEFAULT now(), segment_status text,
                          status_updated_at timestamptz, segment_warn int, segment_errors int)
                        """);
                dsl.execute("CREATE INDEX tree_parent ON process_log_segments(instance_id, instance_created_at, parent_segment_id, segment_id)");
                dsl.execute("CREATE INDEX tree_id ON process_log_segments(instance_id, instance_created_at, segment_id)");
                dsl.execute("CREATE INDEX tree_iteration ON process_log_segments(instance_id, instance_created_at, parent_segment_id, segment_id) WHERE segment_kind = 'ITERATION'");
                dsl.execute("CREATE INDEX tree_attempt ON process_log_segments(instance_id, instance_created_at, parent_segment_id, segment_iteration_index, segment_attempt_index, segment_id) WHERE segment_kind = 'ATTEMPT'");
                var key = new ProcessKey(UUID.randomUUID(), OffsetDateTime.parse("2026-10-19T00:00:00Z"));
                dsl.execute("""
                        INSERT INTO process_log_segments(instance_id, instance_created_at, segment_id,
                          parent_segment_id, segment_name, segment_kind, segment_iteration_index, segment_status)
                        SELECT ?::uuid, ?::timestamptz, n, CASE WHEN n > 1 THEN 1 END,
                          'node', CASE WHEN n > 1 THEN 'ITERATION'::log_segment_kind END,
                          CASE WHEN n > 1 THEN n END, 'OK' FROM generate_series(1, 200001) n
                        """, key.getInstanceId(), key.getCreatedAt());
                dsl.execute("ANALYZE process_log_segments");
                var queries = new ArrayList<String>();
                var cfg = dsl.configuration().derive(new DefaultExecuteListener() {
                    @Override
                    public void renderEnd(ExecuteContext ctx) {
                        if (ctx.query() != null) queries.add(ctx.query().getSQL(ParamType.INLINED));
                    }
                });
                var dao = new ProcessLogTreeDao(cfg);
                var root = dao.page(key, null, null, 100);
                assertTrue(root.items().getFirst().hasChildren());
                assertTrue(root.items().getFirst().hasIterations());
                var leaves = dao.page(key, 1L, null, 100);
                assertEquals(100, leaves.items().size());
                assertTrue(leaves.next());
                assertTrue(leaves.items().stream().noneMatch(n -> n.hasChildren() || n.hasIterations()));
                for (var sql : List.copyOf(queries)) {
                    assertBoundedPlan(dsl, sql);
                }
            }
        }
    }

    private static void assertBoundedPlan(org.jooq.DSLContext dsl, String sql) throws Exception {
        var json = dsl.fetch("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql).getValue(0, 0).toString();
        var plan = new ObjectMapper().readTree(json).get(0).get("Plan");
        assertTrue(tupleVisits(plan) < 10000, json);
        assertTrue(plan.path("Shared Hit Blocks").asLong() + plan.path("Shared Read Blocks").asLong() < 10000, json);
    }

    private static long tupleVisits(JsonNode plan) {
        long visits = 0;
        if (plan.has("Relation Name")) {
            visits = (plan.path("Actual Rows").asLong() + plan.path("Rows Removed by Filter").asLong()
                    + plan.path("Rows Removed by Index Recheck").asLong()) * plan.path("Actual Loops").asLong();
        }
        for (var child : plan.path("Plans")) visits += tupleVisits(child);
        return visits;
    }
}
