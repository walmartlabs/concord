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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MigrationTest {

    private static String dbImage;

    @BeforeAll
    public static void setUp() throws Exception {
        var props = new Properties();
        props.load(MigrationTest.class.getClassLoader().getResourceAsStream("db.properties"));
        dbImage = props.getProperty("db.image");
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void regularMigration() {
        try (var db = new PostgreSQLContainer<>(dbImage)) {
            db.start();
            applyMigrations(db);
        }
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void concurrentMigrations() {
        try (var db = new PostgreSQLContainer<>(dbImage)) {
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
