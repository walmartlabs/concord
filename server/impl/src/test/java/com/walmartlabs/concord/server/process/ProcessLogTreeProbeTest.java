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
                          instance_id uuid NOT NULL, instance_created_at timestamptz NOT NULL, segment_id bigint NOT NULL,
                          parent_segment_id bigint, segment_name text NOT NULL, segment_kind log_segment_kind,
                          segment_iteration_index int, segment_attempt_index int, segment_thread int,
                          correlation_id uuid, segment_ts timestamptz NOT NULL DEFAULT now(), segment_status text,
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
                dsl.execute("CREATE TABLE process_log_segments_existing_child () INHERITS (process_log_segments)");
                conn.setAutoCommit(false);
                try (var in = getClass().getResourceAsStream("/com/walmartlabs/concord/server/db/tree-membership.sql")) {
                    assertNotNull(in);
                    dsl.execute(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
                conn.commit();
                conn.setAutoCommit(true);
                dsl.execute("ANALYZE process_log_segment_membership");
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
                queries.clear();
                var latest = dao.descendants(key, 1, null, null, 2);
                assertEquals(List.of(200001L, 200000L), latest.items().stream().map(n -> n.segment().id()).toList());
                assertEquals(List.of(199999L, 199998L), dao.descendants(key, 1, 200000L, null, 2).items().stream().map(n -> n.segment().id()).toList());
                assertEquals(List.of(200000L, 200001L), dao.descendants(key, 1, null, 199999L, 2).items().stream().map(n -> n.segment().id()).toList());
                queries.clear();
                assertTrue(dao.descendants(key, 1, null, 200001L, 100).items().isEmpty());
                dsl.execute("""
                        INSERT INTO process_log_segments(instance_id, instance_created_at, segment_id, parent_segment_id, segment_name)
                        VALUES (?::uuid, ?::timestamptz, 200002, 1, 'new poll node')
                        """, key.getInstanceId(), key.getCreatedAt());
                var polled = dao.descendants(key, 1, null, 200001L, 100);
                assertEquals(List.of(200002L), polled.items().stream().map(n -> n.segment().id()).toList());
                assertEquals(200002L, polled.nextId());
                assertFalse(polled.next());
                assertTrue(dao.descendants(key, 1, null, polled.nextId(), 100).items().isEmpty());
                for (var sql : queries.stream().filter(q -> q.contains("process_log_segment_membership")).toList()) {
                    assertBoundedPlan(dsl, sql);
                }
                // Transactional mutation, out-of-order insertion, parent-ID guards,
                // and both parts of ProcessKey are part of membership semantics.
                dsl.execute("""
                        INSERT INTO process_log_segments(instance_id, instance_created_at, segment_id, parent_segment_id, segment_name)
                        VALUES (?::uuid, ?::timestamptz, 300003, 300002, 'out of order child')
                        """, key.getInstanceId(), key.getCreatedAt());
                dsl.execute("""
                        INSERT INTO process_log_segments(instance_id, instance_created_at, segment_id, parent_segment_id, segment_name)
                        VALUES (?::uuid, ?::timestamptz, 300002, 1, 'late parent')
                        """, key.getInstanceId(), key.getCreatedAt());
                assertEquals(List.of(300002L, 300003L), dao.descendants(key, 1, null, 300001L, 10).items().stream().map(n -> n.segment().id()).toList());
                dsl.execute("UPDATE process_log_segments SET parent_segment_id = 2 WHERE segment_id = 300002");
                assertEquals(List.of(300003L, 300002L), dao.descendants(key, 2, null, null, 10).items().stream().map(n -> n.segment().id()).toList());
                assertTrue(dao.descendants(new ProcessKey(key.getInstanceId(), key.getCreatedAt().plusDays(1)), 1, null, null, 10).items().isEmpty());
                assertTrue(dao.descendants(new ProcessKey(UUID.randomUUID(), key.getCreatedAt()), 1, null, null, 10).items().isEmpty());
                dsl.execute("UPDATE process_log_segments SET parent_segment_id = 300004 WHERE segment_id = 300002");
                assertTrue(dao.descendants(key, 2, null, null, 10).items().isEmpty());
                dsl.execute("UPDATE process_log_segments SET parent_segment_id = 2 WHERE segment_id = 300002");
                dsl.execute("DELETE FROM process_log_segments WHERE segment_id = 300002");
                assertTrue(dao.descendants(key, 2, null, null, 10).items().isEmpty());
                assertEquals(List.of(300003L), dao.descendants(key, 300002, null, null, 10).items().stream().map(n -> n.segment().id()).toList());
                conn.setAutoCommit(false);
                dsl.execute("""
                        INSERT INTO process_log_segments(instance_id, instance_created_at, segment_id, parent_segment_id, segment_name)
                        VALUES (?::uuid, ?::timestamptz, 400000, 1, 'rolled back node')
                        """, key.getInstanceId(), key.getCreatedAt());
                assertEquals(400000L, dao.descendants(key, 1, null, null, 1).items().getFirst().segment().id());
                conn.rollback();
                conn.setAutoCommit(true);
                assertTrue(dao.descendants(key, 1, null, 399999L, 1).items().isEmpty());
                // Legacy inheritance does not clone row triggers. Cover both
                // installation-time discovery and explicit future provisioning.
                dsl.execute("""
                        INSERT INTO process_log_segments_existing_child(instance_id, instance_created_at, segment_id, parent_segment_id, segment_name)
                        VALUES (?::uuid, ?::timestamptz, 500000, 1, 'existing inheritance child')
                        """, key.getInstanceId(), key.getCreatedAt());
                assertEquals(List.of(500000L), dao.descendants(key, 1, null, 499999L, 10).items().stream().map(n -> n.segment().id()).toList());
                dsl.execute("CREATE TABLE process_log_segments_future_child () INHERITS (process_log_segments)");
                dsl.execute("SELECT provision_process_log_segment_membership()");
                dsl.execute("SELECT provision_process_log_segment_membership()");
                dsl.execute("""
                        INSERT INTO process_log_segments_future_child(instance_id, instance_created_at, segment_id, parent_segment_id, segment_name)
                        VALUES (?::uuid, ?::timestamptz, 500001, 500000, 'future inheritance child')
                        """, key.getInstanceId(), key.getCreatedAt());
                assertEquals(List.of(500001L), dao.descendants(key, 500000, null, null, 10).items().stream().map(n -> n.segment().id()).toList());
                dsl.execute("UPDATE process_log_segments_future_child SET parent_segment_id = 2");
                assertTrue(dao.descendants(key, 500000, null, null, 10).items().isEmpty());
                assertEquals(List.of(500001L), dao.descendants(key, 2, null, null, 10).items().stream().map(n -> n.segment().id()).toList());
                dsl.execute("DELETE FROM process_log_segments_future_child");
                assertTrue(dao.descendants(key, 2, null, null, 10).items().isEmpty());
                // ProcessCleaner deletes through the inheritance parent, not ONLY.
                dsl.execute("DELETE FROM process_log_segments");
                assertEquals(0, dsl.fetchCount(DSL.table("process_log_segment_membership")));
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
