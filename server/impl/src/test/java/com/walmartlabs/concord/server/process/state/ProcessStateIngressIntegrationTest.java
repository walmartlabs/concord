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
import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.db.DatabaseModule;
import com.walmartlabs.concord.db.MainDBChangeLogProvider;
import com.walmartlabs.concord.server.cfg.ProcessConfiguration;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessStateIngressIntegrationTest {
    private static PostgreSQLContainer<?> database;
    private static DataSource dataSource;
    private static DSLContext sql;
    private static ProcessStateManager manager;

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
        ProcessConfiguration processConfig = mock(ProcessConfiguration.class);
        when(processConfig.getSecureFiles()).thenReturn(Collections.emptyList());
        manager = new ProcessStateManager(jooq, mock(SecretStoreConfiguration.class), processConfig, null, null, null);
    }

    private static String encoded(String s) { return Base64.getEncoder().encodeToString(s.getBytes(UTF_8)); }

    @AfterAll
    static void stop() throws Exception {
        if (dataSource != null) dataSource.getClass().getMethod("close").invoke(dataSource);
        if (database != null) database.stop();
    }

    @Test
    void replaceAndImportsPreserveActorRowAndSkipUntrustedBytes(@TempDir Path workspace) throws Exception {
        ProcessKey key = ProcessKey.random();
        byte[] actor = {1, 2, 3, 4};
        sql.transaction(cfg -> DSL.using(cfg).execute("insert into process_state (instance_id, instance_created_at, item_path, item_data, is_encrypted, unix_mode) values (?, cast(? as timestamptz), ?, ?, ?, ?)",
                key.getInstanceId(), key.getCreatedAt().toString(), ".concord/current_user", actor, true, 416));
        Files.createDirectories(workspace.resolve(".concord"));
        Files.writeString(workspace.resolve(".concord/current_user"), "untrusted actor");
        Files.writeString(workspace.resolve("ordinary"), "new state");

        manager.replacePath(key, workspace, (p, attrs) -> {
            assertNotEquals("current_user", p.getFileName().toString());
            return true;
        });
        var row = sql.fetchOne("select item_data, is_encrypted, unix_mode from process_state where instance_id = ? and item_path = ?", key.getInstanceId(), ".concord/current_user");
        assertArrayEquals(actor, row.get(0, byte[].class));
        assertEquals(true, row.get(1));
        assertEquals(416, ((Number) row.get(2)).intValue());
        assertEquals("new state", new String(sql.fetchOne("select item_data from process_state where instance_id = ? and item_path = ?", key.getInstanceId(), "ordinary").get(0, byte[].class), UTF_8));

        manager.importPath(key, ".concord/./", workspace.resolve(".concord"), (p, attrs) -> {
            fail("protected actor reached the caller filter");
            return true;
        });
        assertArrayEquals(actor, sql.fetchOne("select item_data from process_state where instance_id = ? and item_path = ?", key.getInstanceId(), ".concord/current_user").get(0, byte[].class));
    }

    @Test
    void initialImportCannotOverwriteInitiator(@TempDir Path workspace) throws Exception {
        ProcessKey key = ProcessKey.random();
        Files.writeString(workspace.resolve("initiator"), "untrusted initiator");
        Files.writeString(workspace.resolve("other"), "attachment");
        manager.tx(tx -> {
            manager.insertInitial(tx, key, "initiator", "trusted".getBytes(UTF_8));
            manager.importPathInitial(tx, key, null, workspace, (p, attrs) -> true);
        });
        assertEquals("trusted", new String(sql.fetchOne("select item_data from process_initial_state where instance_id = ? and item_path = ?", key.getInstanceId(), "initiator").get(0, byte[].class), UTF_8));
        assertNotNull(sql.fetchOne("select item_data from process_initial_state where instance_id = ? and item_path = ?", key.getInstanceId(), "other"));
    }
}
