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

import com.walmartlabs.concord.server.process.logs.ProcessLogTreeDao;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProcessLogTreeTest {
    @Test
    void pagesChildrenSearchesUnloadedBranchesAndRefreshesStatuses() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:14.21-alpine")) {
            db.start();
            try (var conn = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword())) {
                var dsl = DSL.using(conn, SQLDialect.POSTGRES);
                dsl.execute("CREATE TYPE log_segment_kind AS ENUM ('STEP', 'ITERATION', 'ATTEMPT', 'ERROR', 'PARALLEL')");
                dsl.execute("""
                        CREATE TABLE process_log_segments (
                          instance_id uuid, instance_created_at timestamptz, segment_id bigint,
                          parent_segment_id bigint, segment_name text, segment_kind log_segment_kind, segment_iteration_index int, segment_attempt_index int,
                          segment_thread int, correlation_id uuid, segment_ts timestamptz DEFAULT now(),
                          segment_status text, status_updated_at timestamptz, segment_warn int, segment_errors int
                        )
                        """);
                dsl.execute("CREATE INDEX tree_parent ON process_log_segments(instance_id, instance_created_at, parent_segment_id, segment_id)");
                dsl.execute("CREATE INDEX tree_id ON process_log_segments(instance_id, instance_created_at, segment_id)");
                conn.setAutoCommit(false);
                try (var in = java.util.Objects.requireNonNull(getClass().getResourceAsStream("/com/walmartlabs/concord/server/db/tree-membership.sql"))) {
                    dsl.execute(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
                conn.commit();
                conn.setAutoCommit(true);
                var key = new ProcessKey(UUID.randomUUID(), OffsetDateTime.parse("2026-10-19T00:00:00Z"));
                var dao = new ProcessLogTreeDao(dsl.configuration());
                dsl.execute("INSERT INTO process_log_segments(instance_id, instance_created_at, segment_id, segment_name, segment_status) VALUES (?::uuid, ?::timestamptz, 0, 'System', NULL), (?::uuid, ?::timestamptz, 1, 'loop', 'RUNNING')",
                        key.getInstanceId(), key.getCreatedAt(), key.getInstanceId(), key.getCreatedAt());
                dsl.execute("""
                        INSERT INTO process_log_segments(instance_id, instance_created_at, segment_id, parent_segment_id, segment_name, segment_kind, segment_iteration_index, segment_status)
                        SELECT ?::uuid, ?::timestamptz, n, 1, 'item ' || n, 'ITERATION'::log_segment_kind, n-2, 'OK' FROM generate_series(2, 10001) n
                        """, key.getInstanceId(), key.getCreatedAt());
                var root = dao.page(key, null, null, 100);
                assertEquals(List.of(0L, 1L), root.items().stream().map(n -> n.segment().id()).toList());
                assertTrue(root.items().getLast().hasChildren());
                assertTrue(root.items().getLast().hasIterations());
                // Search is global even without filters; listing without a parent returns roots only.
                var all = dao.search(key, null, 3, null, null, false, false);
                assertEquals(List.of(0L, 1L, 2L), all.items().stream().map(n -> n.segment().id()).toList());
                assertTrue(all.next());

                assertFalse(root.next());
                var first = dao.page(key, 1L, null, 100);
                assertEquals(100, first.items().size());
                assertEquals(101L, first.nextId());
                assertTrue(first.next());
                var second = dao.page(key, 1L, first.nextId(), 100);
                assertEquals(102L, second.items().getFirst().segment().id());
                assertEquals(201L, second.nextId());
                assertTrue(dao.page(key, 2L, null, 100).items().isEmpty());

                dsl.execute("UPDATE process_log_segments SET segment_name = 'hidden 100%_target', segment_status = 'FAILED', segment_errors = 1 WHERE segment_id = 9999");
                var search = dao.search(key, null, 100, "100%_target", null, false, false);
                assertEquals(List.of(9999L), search.items().stream().map(n -> n.segment().id()).toList());
                assertEquals(List.of(1L), search.ancestors().stream().map(n -> n.segment().id()).toList());
                assertEquals(9999L, dao.search(key, null, 1, null, null, true, false).items().getFirst().segment().id());
                assertEquals(LogSegmentTreeNode.Category.FAILED, dao.nodes(key, List.of(9999L)).getFirst().category());
                dsl.execute("UPDATE process_log_segments SET segment_status = 'OK' WHERE segment_id = 1");
                var handled = dao.nodes(key, List.of(9999L)).getFirst();
                assertEquals(LogSegmentTreeNode.Category.FAILED, handled.category());
                assertEquals(9999L, dao.search(key, null, 1, null, null, true, false).items().getFirst().segment().id());
                assertTrue(dao.path(new ProcessKey(UUID.randomUUID(), key.getCreatedAt()), List.of(9999L)).isEmpty());
                assertTrue(dao.nodes(new ProcessKey(key.getInstanceId(), key.getCreatedAt().plusDays(1)), List.of(9999L)).isEmpty());

                dsl.execute("UPDATE process_log_segments SET segment_status = 'RUNNING' WHERE segment_id = 1");
                dsl.execute("UPDATE process_log_segments SET segment_kind = 'ATTEMPT', segment_iteration_index = NULL, segment_attempt_index = 0 WHERE segment_id = 9999");
                dsl.execute("UPDATE process_log_segments SET segment_kind = 'ATTEMPT', segment_iteration_index = NULL, segment_attempt_index = 1 WHERE segment_id = 10000");
                assertTrue(dao.nodes(key, List.of(9999L)).getFirst().retried());
                assertEquals(9999L, dao.search(key, null, 1, null, null, true, false).items().getFirst().segment().id());
                // Old flat segments remain root nodes; null statuses are categorized as OK.
                assertEquals(LogSegmentTreeNode.Category.OK, dao.nodes(key, List.of(0L)).getFirst().category());
                // A loop's details belong to its first iteration's latest retry attempt.
                var correlationId = UUID.randomUUID();
                dsl.execute("UPDATE process_log_segments SET correlation_id = ? WHERE segment_id IN (1, 2, 9999, 10000)", correlationId);
                dsl.execute("UPDATE process_log_segments SET parent_segment_id = 2 WHERE segment_id IN (9999, 10000)");
                assertEquals(10000L, dao.run(key, 1).id());
                assertEquals(10000L, dao.run(key, 2).id());
                assertNull(dao.run(key, 999999));
                // Pagination walks the entire subtree, regardless of expanded branches.
                var latest = dao.descendants(key, 1, null, null, 2);
                assertEquals(List.of(10001L, 10000L), latest.items().stream().map(n -> n.segment().id()).toList());
                assertTrue(latest.next());
                assertTrue(latest.ancestors().stream().anyMatch(n -> n.segment().id() == 2));
                var older = dao.descendants(key, 1, latest.nextId(), null, 2);
                assertEquals(List.of(9999L, 9998L), older.items().stream().map(n -> n.segment().id()).toList());
                var newer = dao.descendants(key, 1, null, 9998L, 2);
                assertEquals(List.of(9999L, 10000L), newer.items().stream().map(n -> n.segment().id()).toList());
                assertTrue(newer.next());
                assertEquals(List.of(10001L), dao.descendants(key, 1, null, newer.nextId(), 2).items().stream().map(n -> n.segment().id()).toList());
                // Interleaved IDs from sibling branches must never leak into a selected subtree.
                var nested = dao.descendants(key, 2, null, null, 10);
                assertEquals(List.of(10000L, 9999L), nested.items().stream().map(n -> n.segment().id()).toList());
                assertFalse(nested.ancestors().stream().anyMatch(n -> n.segment().id() == 1));
                assertTrue(dao.descendants(key, 0, null, null, 10).items().isEmpty());
                assertTrue(dao.descendants(new ProcessKey(UUID.randomUUID(), key.getCreatedAt()), 1, null, null, 10).items().isEmpty());

                // Failures under successful parents remain Failed, including across page boundaries.
                dsl.execute("""
                        INSERT INTO process_log_segments(instance_id, instance_created_at, segment_id,
                            parent_segment_id, segment_name, segment_status)
                        SELECT ?::uuid, ?::timestamptz, n,
                            CASE WHEN n BETWEEN 20001 AND 20003 THEN 20000 ELSE NULL END,
                            'failed-test', CASE WHEN n = 20000 THEN 'OK' ELSE 'FAILED' END
                        FROM generate_series(20000, 20005) n
                        """, key.getInstanceId(), key.getCreatedAt());
                var failures = dao.search(key, 20000L, 1, "failed-test", LogSegmentTreeNode.Category.FAILED, false, false);
                assertEquals(List.of(20001L), failures.items().stream().map(n -> n.segment().id()).toList());
                assertTrue(failures.next());
                var lastFailure = dao.search(key, failures.nextId(), 1, "failed-test", LogSegmentTreeNode.Category.FAILED, false, false);
                assertEquals(List.of(20002L), lastFailure.items().stream().map(n -> n.segment().id()).toList());
                assertTrue(lastFailure.next());
                var backwards = dao.search(key, 20005L, 1, "failed-test", LogSegmentTreeNode.Category.FAILED, true, true);
                assertEquals(List.of(20004L), backwards.items().stream().map(n -> n.segment().id()).toList());
                assertTrue(backwards.next());
                dsl.execute("""
                        INSERT INTO process_log_segments(instance_id, instance_created_at, segment_id,
                            segment_name, segment_status, segment_warn, segment_errors)
                        SELECT ?::uuid, ?::timestamptz, n, 'warnings-test',
                            CASE WHEN n BETWEEN 30000 AND 30003 THEN 'FAILED'
                                 WHEN n = 30005 THEN 'RUNNING' ELSE 'OK' END,
                            CASE WHEN n IN (30004, 30005) THEN 1 ELSE 0 END,
                            CASE WHEN n = 30006 THEN 1 ELSE 0 END
                        FROM generate_series(30000, 30006) n
                        """, key.getInstanceId(), key.getCreatedAt());
                var warnings = dao.search(key, null, 1, "warnings-test", LogSegmentTreeNode.Category.WARNINGS, false, false);
                assertEquals(List.of(30004L), warnings.items().stream().map(n -> n.segment().id()).toList());
                assertTrue(warnings.next());
                var moreWarnings = dao.search(key, warnings.nextId(), 1, "warnings-test", LogSegmentTreeNode.Category.WARNINGS, false, false);
                assertEquals(List.of(30006L), moreWarnings.items().stream().map(n -> n.segment().id()).toList());
                assertFalse(moreWarnings.next());
                var warningErrors = dao.search(key, null, 1, "warnings-test", LogSegmentTreeNode.Category.WARNINGS, true, true);
                assertEquals(List.of(30006L), warningErrors.items().stream().map(n -> n.segment().id()).toList());
                assertFalse(warningErrors.next());
                dsl.execute("""
                        INSERT INTO process_log_segments(instance_id, instance_created_at, segment_id,
                            segment_name, segment_status, segment_warn, segment_errors)
                        VALUES (?::uuid, ?::timestamptz, 40000, 'ok-test', 'OK', 0, 0),
                               (?::uuid, ?::timestamptz, 40001, 'ok-test', NULL, NULL, NULL),
                               (?::uuid, ?::timestamptz, 40002, 'ok-test', 'OK', 1, 0),
                               (?::uuid, ?::timestamptz, 40003, 'ok-test', 'OK', 0, 1)
                        """, key.getInstanceId(), key.getCreatedAt(), key.getInstanceId(), key.getCreatedAt(),
                        key.getInstanceId(), key.getCreatedAt(), key.getInstanceId(), key.getCreatedAt());
                var ok = dao.search(key, null, 1, "ok-test", LogSegmentTreeNode.Category.OK, false, false);
                assertEquals(List.of(40000L), ok.items().stream().map(n -> n.segment().id()).toList());
                assertTrue(ok.next());
                var moreOk = dao.search(key, ok.nextId(), 1, "ok-test", LogSegmentTreeNode.Category.OK, false, false);
                assertEquals(List.of(40001L), moreOk.items().stream().map(n -> n.segment().id()).toList());
                assertFalse(moreOk.next());
                assertFalse(moreOk.items().getFirst().retried());
                var reverseOk = dao.search(key, 40001L, 1, "ok-test", LogSegmentTreeNode.Category.OK, false, true);
                assertEquals(List.of(40000L), reverseOk.items().stream().map(n -> n.segment().id()).toList());
                assertFalse(reverseOk.next());
                assertTrue(dao.search(key, null, 100, null, LogSegmentTreeNode.Category.OK, true, false).items().isEmpty());

                dsl.execute("""
                        INSERT INTO process_log_segments(instance_id, instance_created_at, segment_id,
                            parent_segment_id, segment_name, segment_status, segment_errors, segment_warn)
                        SELECT ?::uuid, ?::timestamptz, n, 40000, 'running-test',
                            CASE WHEN n = 50001 THEN 'SUSPENDED' WHEN n = 50002 THEN 'OK' ELSE 'RUNNING' END,
                            CASE WHEN n = 50001 THEN 1 ELSE 0 END, 1
                        FROM generate_series(50000, 50002) n
                        """, key.getInstanceId(), key.getCreatedAt());
                var running = dao.search(key, null, 1, "running-test", LogSegmentTreeNode.Category.RUNNING, false, false);
                assertEquals(List.of(50000L), running.items().stream().map(n -> n.segment().id()).toList());
                assertEquals(List.of(40000L), running.ancestors().stream().map(n -> n.segment().id()).toList());
                assertTrue(running.next());
                assertFalse(running.items().getFirst().retried());
                var suspended = dao.search(key, running.nextId(), 1, "running-test", LogSegmentTreeNode.Category.RUNNING, false, false);
                assertEquals(List.of(50001L), suspended.items().stream().map(n -> n.segment().id()).toList());
                assertFalse(suspended.next());
                assertEquals(LogSegmentTreeNode.Category.RUNNING, suspended.items().getFirst().category());
                var runningErrors = dao.search(key, null, 1, "running-test", LogSegmentTreeNode.Category.RUNNING, true, true);
                assertEquals(List.of(50001L), runningErrors.items().stream().map(n -> n.segment().id()).toList());
                assertFalse(runningErrors.next());
                var reverseRunning = dao.search(key, 50001L, 1, "running-test", LogSegmentTreeNode.Category.RUNNING, false, true);
                assertEquals(List.of(50000L), reverseRunning.items().stream().map(n -> n.segment().id()).toList());
                assertFalse(reverseRunning.next());

                // Attempts of different iterations share a parent but are not each other's retries.
                dsl.execute("""
                        INSERT INTO process_log_segments(instance_id, instance_created_at, segment_id,
                            parent_segment_id, segment_name, segment_kind, segment_status,
                            segment_iteration_index, segment_attempt_index)
                        VALUES (?::uuid, ?::timestamptz, 60000, 1, 'a', 'ATTEMPT', 'FAILED', 0, 0),
                               (?::uuid, ?::timestamptz, 60001, 1, 'b', 'ATTEMPT', 'OK', 1, 1),
                               (?::uuid, ?::timestamptz, 60002, 1, 'c', 'ATTEMPT', 'FAILED', 2, 0),
                               (?::uuid, ?::timestamptz, 60003, 1, 'c', 'ATTEMPT', 'OK', 2, 1)
                        """, key.getInstanceId(), key.getCreatedAt(), key.getInstanceId(), key.getCreatedAt(),
                        key.getInstanceId(), key.getCreatedAt(), key.getInstanceId(), key.getCreatedAt());
                var attempts = dao.nodes(key, List.of(60000L, 60001L, 60002L, 60003L));
                assertFalse(attempts.get(0).retried());
                assertTrue(attempts.get(2).retried());
                assertEquals(2, attempts.get(2).segment().iterationIndex());
                assertEquals(0, attempts.get(2).segment().attemptIndex());

                var warningsForFailures = dao.search(key, null, 10, "failed-test", LogSegmentTreeNode.Category.WARNINGS, false, false);
                assertTrue(warningsForFailures.items().isEmpty());




            }
        }
    }
}
