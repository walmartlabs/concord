package com.walmartlabs.concord.db;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MigrationTest {

    private static String dbImage;

    @BeforeAll
    public static void setUp() throws Exception {
        var props = new Properties();
        props.load(MigrationTest.class.getClassLoader().getResourceAsStream("db.properties"));
        dbImage = props.getProperty("db.image");
    }

    @SuppressWarnings("resource")
    private PostgreSQLContainer<?> createDbContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse(dbImage)
                .asCompatibleSubstituteFor("postgres"))
                .waitingFor(Wait.forListeningPort());
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void regularMigration() throws Exception {
        try (var db = createDbContainer()) {
            db.start();
            applyMigrations(db);
            assertLogSegmentLookupPlan(db);
            assertPartitionedEventsMigrationIsMarkedRan(db);
        }
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void concurrentMigrations() {
        try (var db = createDbContainer()) {
            db.start();

            var threads = new Thread[5];

            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> applyMigrations(db), "migration#" + i);
            }

            for (Thread value : threads) {
                value.start();
            }

            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void applyMigrations(PostgreSQLContainer<?> db) {
        var cfg = new DatabaseConfigurationImpl(db.getJdbcUrl(), db.getUsername(), db.getPassword());
        DataSourceUtils.migrateDb(cfg, new MainDBChangeLogProvider());
    }

    private static void assertLogSegmentLookupPlan(PostgreSQLContainer<?> db) throws Exception {
        try (var conn = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
             var statement = conn.createStatement()) {
            conn.setAutoCommit(false);
            statement.executeUpdate("""
                    insert into process_events (
                        instance_id, instance_created_at, event_type, event_date, event_data
                    )
                    select '00000000-0000-0000-0000-000000000001'::uuid,
                           '2026-01-01T00:00:00Z'::timestamptz,
                           'ELEMENT',
                           '2026-01-01T00:00:00Z'::timestamptz + g * interval '1 millisecond',
                           jsonb_build_object(
                               'logSegmentId', (g % 5000)::text,
                               'phase', case when g % 2 = 0 then 'pre' else 'post' end)
                    from generate_series(1, 100000) g
                    """);
            statement.execute("analyze process_events");

            try (var result = statement.executeQuery("""
                    explain (analyze, buffers, costs off)
                    select event_seq
                    from process_events
                    where instance_id = '00000000-0000-0000-0000-000000000001'::uuid
                      and instance_created_at = '2026-01-01T00:00:00Z'::timestamptz
                      and ((event_data::jsonb ->> 'logSegmentId')::varchar) = '42'
                      and event_type = 'ELEMENT'
                    order by event_seq
                    """)) {
                var plan = new StringJoiner(System.lineSeparator());
                while (result.next()) {
                    plan.add(result.getString(1));
                }
                var text = plan.toString().toLowerCase();
                assertTrue(text.contains("idx_proc_events_log_segment"), plan::toString);
                assertFalse(text.contains("seq scan on process_events"), plan::toString);
            }

            long previous = -1;
            int count = 0;
            try (var result = statement.executeQuery("""
                    select event_seq
                    from process_events
                    where instance_id = '00000000-0000-0000-0000-000000000001'::uuid
                      and instance_created_at = '2026-01-01T00:00:00Z'::timestamptz
                      and ((event_data::jsonb ->> 'logSegmentId')::varchar) = '42'
                      and event_type = 'ELEMENT'
                    order by event_seq
                    """)) {
                while (result.next()) {
                    long current = result.getLong(1);
                    assertTrue(current > previous);
                    previous = current;
                    count += 1;
                }
            }
            assertEquals(20, count);
            conn.rollback();
        }
    }

    private static void assertPartitionedEventsMigrationIsMarkedRan(PostgreSQLContainer<?> db) throws Exception {
        try (var conn = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
             var statement = conn.createStatement()) {
            statement.execute("create schema partitioned_fixture");
            statement.execute("set search_path to partitioned_fixture");
            statement.execute("create table process_log_segments (segment_thread integer)");
            statement.execute("""
                    create table process_events (
                        instance_id uuid not null,
                        instance_created_at timestamptz not null,
                        event_type varchar(36) not null,
                        event_data jsonb not null,
                        event_seq bigint not null
                    ) partition by range (instance_created_at)
                    """);
            statement.execute("""
                    create table process_events_2026 partition of process_events
                    for values from ('2026-01-01') to ('2027-01-01')
                    """);

            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            database.setDefaultSchemaName("partitioned_fixture");
            var liquibase = new Liquibase(
                    "com/walmartlabs/concord/server/db/v2.46.1.xml",
                    new ClassLoaderResourceAccessor(),
                    database);
            liquibase.update((String) null);

            try (var result = statement.executeQuery("""
                    select exectype
                    from partitioned_fixture.databasechangelog
                    where id = '2461010'
                    """)) {
                assertTrue(result.next());
                assertEquals("MARK_RAN", result.getString(1));
            }
            try (var result = statement.executeQuery("""
                    select to_regclass('partitioned_fixture.idx_proc_events_log_segment') is not null
                    """)) {
                assertTrue(result.next());
                assertFalse(result.getBoolean(1));
            }
        }
    }

    private static String base64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(UTF_8));
    }

    private static final class DatabaseConfigurationImpl implements DatabaseConfiguration {

        private final String url;
        private final String username;
        private final String password;

        private DatabaseConfigurationImpl(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        @Override
        public String url() {
            return url;
        }

        @Override
        public String username() {
            return username;
        }

        @Override
        public String password() {
            return password;
        }

        @Override
        public int maxPoolSize() {
            return 10;
        }

        @Override
        public Duration maxLifetime() {
            return Duration.ofMinutes(30);
        }

        @Override
        public Map<String, Object> changeLogParameters() {
            return Map.of(
                    "createExtensionAvailable", "true",
                    "defaultAdminToken", base64("foobar"),
                    "skipAdminTokenGeneration", "false",
                    "defaultAgentToken", base64("barbaz"),
                    "skipAgentTokenGeneration", "false",
                    "secretStoreSalt", base64("foo"),
                    "serverPassword", base64("bar"));
        }
    }
}
