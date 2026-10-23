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
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ProcessLogAppendTest {
    @Test
    void serializesBothGlobalAndSegmentRangesBeforeReadingOffsets() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:14.21-alpine")) {
            db.start();
            try (var first = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var second = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var observer = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var workers = Executors.newFixedThreadPool(2)) {
                var one = DSL.using(first, SQLDialect.POSTGRES);
                var two = DSL.using(second, SQLDialect.POSTGRES);
                var monitor = DSL.using(observer, SQLDialect.POSTGRES);
                ProcessLogDataTest.schema(monitor);
                var secondPid = two.fetchOne("select pg_backend_pid()").get(0, Integer.class);
                for (long secondSegment : new long[]{1, 2}) {
                    var key = new ProcessKey(UUID.randomUUID(), OffsetDateTime.parse("2026-10-19T00:00:00Z"));
                    var appended = new CountDownLatch(1);
                    var releaseCommit = new CountDownLatch(1);
                    var a = workers.submit(() -> one.transaction(cfg -> {
                        new ProcessLogsDao(cfg).append(key, 1, new byte[]{65, 66});
                        appended.countDown();
                        assertTrue(releaseCommit.await(10, TimeUnit.SECONDS));
                    }));
                    try {
                        assertTrue(appended.await(10, TimeUnit.SECONDS));
                        var b = workers.submit(() -> new ProcessLogsDao(two.configuration())
                                .append(key, secondSegment, new byte[]{67, 68, 69}));
                        try {
                            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                            boolean waiting = false;
                            while (System.nanoTime() < deadline && !b.isDone()) {
                                waiting = Boolean.TRUE.equals(monitor.fetchOne(
                                        "select exists(select 1 from pg_locks where pid=? and locktype='advisory' and not granted)",
                                        secondPid).get(0, Boolean.class));
                                if (waiting) {
                                    break;
                                }
                                Thread.sleep(10);
                            }
                            assertTrue(waiting, "second append must wait before calculating either offset");
                            assertFalse(b.isDone());
                        } finally {
                            releaseCommit.countDown();
                        }
                        a.get(10, TimeUnit.SECONDS);
                        b.get(10, TimeUnit.SECONDS);
                    } finally {
                        releaseCommit.countDown();
                    }
                    var dao = new ProcessLogsDao(monitor.configuration());
                    var all = dao.data(key, null, null);
                    assertEquals(5, all.getSize());
                    assertEquals(2, all.getChunks().size());
                    assertEquals(0, all.getChunks().getFirst().getStart());
                    assertEquals(2, all.getChunks().getLast().getStart());
                    var segment = dao.segmentData(key, secondSegment, null, null);
                    assertEquals(secondSegment == 1 ? 5 : 3, segment.getSize());
                    assertEquals(secondSegment == 1 ? 2 : 0, segment.getChunks().getLast().getStart());
                    assertEquals(0, monitor.fetchOne("""
                            SELECT count(*)::int FROM process_log_data a JOIN process_log_data b
                              ON a.instance_id=b.instance_id AND a.instance_created_at=b.instance_created_at AND a.log_seq<b.log_seq
                            WHERE a.instance_id=?::uuid AND a.instance_created_at=?::timestamptz
                              AND (a.log_range && b.log_range OR (a.segment_id=b.segment_id AND a.segment_range && b.segment_range))
                            """, key.getInstanceId(), key.getCreatedAt()).get(0, Integer.class));
                    var anotherPartition = new ProcessKey(key.getInstanceId(), key.getCreatedAt().plusDays(1));
                    dao.append(anotherPartition, 1, new byte[]{70});
                    assertEquals(0, dao.data(anotherPartition, null, null).getChunks().getFirst().getStart());
                }
            }
        }
    }
}
