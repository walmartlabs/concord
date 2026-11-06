package com.walmartlabs.concord.server.process.queue;

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

import com.codahale.metrics.MetricRegistry;
import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.db.DatabaseModule;
import com.walmartlabs.concord.db.MainDBChangeLogProvider;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.TestObjectMapper;
import com.walmartlabs.concord.server.process.ProcessKind;
import com.walmartlabs.concord.server.process.waits.WaitProcessStatusListener;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_WAIT_CONDITIONS;
import static org.junit.jupiter.api.Assertions.*;

class ProcessQueueManagerTest {

    private static final OffsetDateTime INITIAL_UPDATED_AT = OffsetDateTime.parse("2020-01-01T00:00:00Z");

    private static PostgreSQLContainer<?> db;
    private static HikariDataSource dataSource;
    private static DSLContext dsl;

    private ProcessQueueDao dao;
    private ProcessQueueManager manager;
    private final List<ProcessKey> notified = new ArrayList<>();
    private ProcessKey enqueued;
    private ProcessKey waiting;
    private ProcessKey finished;

    @BeforeAll
    static void startDatabase() {
        db = new PostgreSQLContainer<>(DockerImageName.parse(System.getProperty("db.image"))
                .asCompatibleSubstituteFor("postgres"))
                .withCommand("postgres", "-c", "lock_timeout=2s");
        db.start();

        var cfg = new TestDatabaseConfiguration(db.getJdbcUrl(), db.getUsername(), db.getPassword());
        var module = new DatabaseModule();
        dataSource = (HikariDataSource) module.appDataSource(cfg, new MetricRegistry(), Set.of(new MainDBChangeLogProvider()));
        dsl = DSL.using(module.appJooqConfiguration(dataSource));
    }

    @AfterAll
    static void stopDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
        if (db != null) {
            db.close();
        }
    }

    @BeforeEach
    void setUp() {
        dao = new ProcessQueueDao(dsl.configuration(), new ConcordObjectMapper(TestObjectMapper.INSTANCE));
        ProcessStatusListener observer = (tx, key, status) -> notified.add(key);
        manager = new ProcessQueueManager(dao, null, null, null, Set.of(new WaitProcessStatusListener(), observer));
        enqueued = insert(ProcessStatus.ENQUEUED);
        waiting = insert(ProcessStatus.WAITING);
        finished = insert(ProcessStatus.FINISHED);
    }

    @Test
    void bulkStatusAndListenerWritesRollBackTogether() {
        var before = dsl.selectFrom(PROCESS_QUEUE)
                .where(PROCESS_QUEUE.INSTANCE_ID.in(enqueued.getInstanceId(), waiting.getInstanceId()))
                .orderBy(PROCESS_QUEUE.INSTANCE_ID).fetch();
        var rollback = new RuntimeException("roll back the caller transaction");

        var thrown = assertThrows(RuntimeException.class, () -> dao.tx(tx -> {
            var updated = manager.updateExpectedStatus(tx, List.of(enqueued, waiting),
                    List.of(ProcessStatus.ENQUEUED, ProcessStatus.WAITING), ProcessStatus.CANCELLED);
            assertEquals(Set.of(enqueued, waiting), Set.copyOf(updated));
            assertStatusAndWait(tx, enqueued, ProcessStatus.CANCELLED, false);
            assertStatusAndWait(tx, waiting, ProcessStatus.CANCELLED, false);
            throw rollback;
        }));

        assertSame(rollback, thrown);
        assertEquals(Set.of(enqueued, waiting), Set.copyOf(notified));
        assertEquals(2, notified.size());
        assertEquals(before, dsl.selectFrom(PROCESS_QUEUE)
                .where(PROCESS_QUEUE.INSTANCE_ID.in(enqueued.getInstanceId(), waiting.getInstanceId()))
                .orderBy(PROCESS_QUEUE.INSTANCE_ID).fetch());
        assertStatusAndWait(dsl, enqueued, ProcessStatus.ENQUEUED, true);
        assertStatusAndWait(dsl, waiting, ProcessStatus.WAITING, true);
    }

    @Test
    void bulkStatusCommitsAndNotifiesOnlyUpdatedKeys() {
        dao.tx(tx -> {
            var updated = manager.updateExpectedStatus(tx, List.of(enqueued, waiting, finished, ProcessKey.random()),
                    List.of(ProcessStatus.ENQUEUED, ProcessStatus.WAITING), ProcessStatus.CANCELLED);
            assertEquals(Set.of(enqueued, waiting), Set.copyOf(updated));
            assertEquals(2, updated.size());
        });

        assertEquals(Set.of(enqueued, waiting), Set.copyOf(notified));
        assertEquals(2, notified.size());
        assertStatusAndWait(dsl, enqueued, ProcessStatus.CANCELLED, false);
        assertStatusAndWait(dsl, waiting, ProcessStatus.CANCELLED, false);
        assertStatusAndWait(dsl, finished, ProcessStatus.FINISHED, true);
        assertTrue(dsl.select(PROCESS_QUEUE.LAST_UPDATED_AT).from(PROCESS_QUEUE)
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(enqueued.getInstanceId()))
                .fetchSingle(PROCESS_QUEUE.LAST_UPDATED_AT).isAfter(INITIAL_UPDATED_AT));
    }

    @Test
    void nullExpectedStatusesDoesNotFilter() {
        dao.tx(tx -> assertEquals(List.of(finished),
                manager.updateExpectedStatus(tx, List.of(finished), null, ProcessStatus.CANCELLED)));

        assertEquals(List.of(finished), notified);
        assertStatusAndWait(dsl, finished, ProcessStatus.CANCELLED, false);
    }

    @Test
    void emptyExpectedStatusesUpdatesNothing() {
        dao.tx(tx -> assertTrue(manager.updateExpectedStatus(tx, List.of(enqueued), List.of(), ProcessStatus.CANCELLED).isEmpty()));

        assertTrue(notified.isEmpty());
        assertStatusAndWait(dsl, enqueued, ProcessStatus.ENQUEUED, true);
    }

    @Test
    void emptyProcessKeysUpdatesNothing() {
        dao.tx(tx -> assertTrue(manager.updateExpectedStatus(tx, List.of(), null, ProcessStatus.CANCELLED).isEmpty()));

        assertTrue(notified.isEmpty());
        assertStatusAndWait(dsl, enqueued, ProcessStatus.ENQUEUED, true);
    }

    @Test
    @Timeout(15)
    void bulkStatusUsesRowsLockedByCaller() {
        dao.tx(tx -> {
            tx.selectFrom(PROCESS_QUEUE)
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(enqueued.getInstanceId()))
                    .forUpdate().fetchSingle();
            assertEquals(List.of(enqueued), manager.updateExpectedStatus(tx, List.of(enqueued),
                    List.of(ProcessStatus.ENQUEUED), ProcessStatus.CANCELLED));
        });

        assertStatusAndWait(dsl, enqueued, ProcessStatus.CANCELLED, false);
    }

    private ProcessKey insert(ProcessStatus status) {
        var key = ProcessKey.random();
        dao.tx(tx -> {
            dao.insert(tx, key, status, ProcessKind.DEFAULT, null, null, null, null, null, null, Map.of(), null);
            tx.update(PROCESS_QUEUE).set(PROCESS_QUEUE.LAST_UPDATED_AT, INITIAL_UPDATED_AT)
                    .where(PROCESS_QUEUE.INSTANCE_ID.eq(key.getInstanceId())).execute();
            tx.insertInto(PROCESS_WAIT_CONDITIONS)
                    .set(PROCESS_WAIT_CONDITIONS.INSTANCE_ID, key.getInstanceId())
                    .set(PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT, key.getCreatedAt())
                    .set(PROCESS_WAIT_CONDITIONS.IS_WAITING, true)
                    .set(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS, JSONB.valueOf("[]"))
                    .execute();
        });
        return dao.getKey(key.getInstanceId());
    }

    private static void assertStatusAndWait(DSLContext tx, ProcessKey key, ProcessStatus status, boolean isWaiting) {
        assertEquals(status.name(), tx.select(PROCESS_QUEUE.CURRENT_STATUS).from(PROCESS_QUEUE)
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(key.getInstanceId())).fetchSingle(PROCESS_QUEUE.CURRENT_STATUS));
        var wait = tx.selectFrom(PROCESS_WAIT_CONDITIONS)
                .where(PROCESS_WAIT_CONDITIONS.INSTANCE_ID.eq(key.getInstanceId())).fetchSingle();
        assertEquals(isWaiting, wait.getIsWaiting());
        assertEquals(isWaiting ? JSONB.valueOf("[]") : null, wait.getWaitConditions());
        assertEquals(isWaiting ? 0L : 1L, wait.getVersion());
    }

    private record TestDatabaseConfiguration(String url, String username, String password) implements DatabaseConfiguration {

        @Override
        public int maxPoolSize() {
            return 3;
        }

        @Override
        public Duration maxLifetime() {
            return Duration.ofMinutes(30);
        }

        @Override
        public Map<String, Object> changeLogParameters() {
            return Map.of("superuserAvailable", "true",
                    "createExtensionAvailable", "true",
                    "defaultAdminToken", "dGVzdA==",
                    "skipAdminTokenGeneration", "true",
                    "defaultAgentToken", "dGVzdA==",
                    "skipAgentTokenGeneration", "true",
                    "secretStoreSalt", "dGVzdA==",
                    "serverPassword", "dGVzdA==");
        }
    }
}
