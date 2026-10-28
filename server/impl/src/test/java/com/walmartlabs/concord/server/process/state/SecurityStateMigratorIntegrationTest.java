package com.walmartlabs.concord.server.process.state;

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
import com.walmartlabs.concord.common.secret.SecretUtils;
import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.db.DatabaseModule;
import com.walmartlabs.concord.db.MainDBChangeLogProvider;
import com.walmartlabs.concord.server.cfg.ProcessConfiguration;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.process.ExecutionIdentityCodec;
import com.walmartlabs.concord.server.process.SecurityStateMigrator;
import com.walmartlabs.concord.server.security.SecurityUtils;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityStateMigratorIntegrationTest {
    private static PostgreSQLContainer<?> database;
    private static DataSource dataSource;
    private static DSLContext sql;
    private static SecurityStateMigrator migrator;
    private static final byte[] PASSWORD = "bar".getBytes(UTF_8);
    private static final byte[] SALT = "foo".getBytes(UTF_8);

    @BeforeAll
    static void start() {
        database = new PostgreSQLContainer<>(DockerImageName.parse(System.getProperty("db.image", "library/postgres:14.21-alpine")).asCompatibleSubstituteFor("postgres"));
        database.start();
        DatabaseConfiguration config = new DatabaseConfiguration() {
            public String url() { return database.getJdbcUrl(); }
            public String username() { return database.getUsername(); }
            public String password() { return database.getPassword(); }
            public int maxPoolSize() { return 8; }
            public Duration maxLifetime() { return Duration.ofMinutes(30); }
            public Map<String, Object> changeLogParameters() {
                return Map.of("createExtensionAvailable", "true", "superuserAvailable", "false", "defaultAdminToken", encoded("foobar"),
                        "skipAdminTokenGeneration", "false", "defaultAgentToken", encoded("barbaz"),
                        "skipAgentTokenGeneration", "false", "secretStoreSalt", encoded("foo"), "serverPassword", encoded("bar"));
            }
        };
        DatabaseModule module = new DatabaseModule(true);
        dataSource = module.appDataSource(config, new MetricRegistry(), Set.of(new MainDBChangeLogProvider()));
        var jooq = module.appJooqConfiguration(dataSource);
        sql = DSL.using(jooq);
        SecretStoreConfiguration secrets = mock(SecretStoreConfiguration.class);
        when(secrets.getServerPwd()).thenReturn(PASSWORD);
        when(secrets.getSecretStoreSalt()).thenReturn(SALT);
        ProcessConfiguration processConfig = mock(ProcessConfiguration.class);
        when(processConfig.getSecureFiles()).thenReturn(Collections.emptyList());
        ProcessStateManager manager = new ProcessStateManager(jooq, secrets, processConfig, null, null, null);
        migrator = new SecurityStateMigrator(jooq, manager);
    }

    private static String encoded(String s) { return Base64.getEncoder().encodeToString(s.getBytes(UTF_8)); }

    @AfterAll
    static void stop() throws Exception {
        if (dataSource != null) dataSource.getClass().getMethod("close").invoke(dataSource);
        if (database != null) database.stop();
    }

    @Test
    void convertsBothTablesInBatchesPreservesMetadataAndResumesAfterFailure() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntry user = new UserEntry(userId, "user", null, "User", Set.of(), UserType.LOCAL, null, Set.of(), false, null, false);
        byte[] legacy = SecurityUtils.serialize(new SimplePrincipalCollection(new UserPrincipal("local", user), "local"));
        OffsetDateTime start = OffsetDateTime.parse("2020-01-01T00:00:00Z");
        UUID encryptedId = UUID.randomUUID();
        for (int i = 0; i < 101; i++) {
            UUID id = i == 0 ? encryptedId : UUID.randomUUID();
            insert("process_initial_state", id, start.plusSeconds(i), "initiator", i == 0 ? SecretUtils.encrypt(legacy, PASSWORD, SALT) : legacy, i == 0, 384);
        }
        UUID broken = UUID.randomUUID();
        byte[] invalid = "not an identity".getBytes(UTF_8);
        insert("process_initial_state", broken, start.plusSeconds(101), "initiator", invalid, false, 448);
        UUID currentId = UUID.randomUUID();
        insert("process_state", currentId, start, ".concord/current_user", legacy, false, 416);
        insert("process_state", UUID.randomUUID(), start, "unrelated", invalid, false, 420);

        assertThrows(IllegalStateException.class, migrator::migrate);
        assertArrayEquals(invalid, data("process_initial_state", broken));
        assertArrayEquals(legacy, data("process_state", currentId));
        byte[] encrypted = data("process_initial_state", encryptedId);
        assertFalse(Arrays.equals(legacy, encrypted));
        var metadata = sql.fetchOne("select is_encrypted, unix_mode from process_initial_state where instance_id = ?", encryptedId);
        assertEquals(true, metadata.get(0));
        assertEquals(384, ((Number) metadata.get(1)).intValue());

        try (var plaintext = SecretUtils.decrypt(new java.io.ByteArrayInputStream(encrypted), PASSWORD, SALT)) {
            assertEquals(userId, ExecutionIdentityCodec.decode(plaintext.readAllBytes()).userId());
        }
        sql.transaction(cfg -> DSL.using(cfg).execute("update process_initial_state set item_data = ? where instance_id = ?", legacy, broken));
        migrator.migrate();
        byte[] converted = data("process_state", currentId);
        assertEquals(userId, ExecutionIdentityCodec.decode(converted).userId());
        assertFalse(ExecutionIdentityCodec.isLegacy(converted));
        migrator.migrate();
        assertArrayEquals(encrypted, data("process_initial_state", encryptedId));
        assertArrayEquals(converted, data("process_state", currentId));
    }

    @Test
    void rejectsLegacyTimestampSchemaInsteadOfLooping() {
        sql.transaction(cfg -> DSL.using(cfg).execute("alter table process_state alter column instance_created_at " +
                "type timestamp without time zone using instance_created_at at time zone 'UTC'"));
        try {
            IllegalStateException error = assertThrows(IllegalStateException.class, migrator::migrate);
            assertTrue(error.getMessage().contains("process_state.instance_created_at must be timestamp with time zone"));
        } finally {
            sql.transaction(cfg -> DSL.using(cfg).execute("alter table process_state alter column instance_created_at " +
                    "type timestamp with time zone using instance_created_at at time zone 'UTC'"));
        }
    }

    private static void insert(String table, UUID id, OffsetDateTime created, String path, byte[] bytes, boolean encrypted, int mode) {
        sql.transaction(cfg -> DSL.using(cfg).execute("insert into " + table + " (instance_id, instance_created_at, item_path, item_data, is_encrypted, unix_mode) values (?, cast(? as timestamptz), ?, ?, ?, ?)",
                id, created.toString(), path, bytes, encrypted, mode));
    }

    private static byte[] data(String table, UUID id) {
        return sql.fetchOne("select item_data from " + table + " where instance_id = ?", id).get(0, byte[].class);
    }
}
