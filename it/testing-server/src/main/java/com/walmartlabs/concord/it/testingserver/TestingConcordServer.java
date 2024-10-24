package com.walmartlabs.concord.it.testingserver;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Module;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;
import com.walmartlabs.concord.server.ConcordServer;
import com.walmartlabs.concord.server.ConcordServerModule;
import org.testcontainers.containers.PostgreSQLContainer;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * A helper class for running concord-server. It runs PostgreSQL in Docker
 * and the server runs in the same JVM as TestingConcordServer.
 */
public class TestingConcordServer implements AutoCloseable {


    private final PostgreSQLContainer<?> db;
    private final Map<String, String> extraConfiguration;
    private final List<Function<Config, Module>> extraModules;
    private final int apiPort;
    private final String adminApiKey;
    private final String agentApiKey;

    private ConcordServer server;

    public TestingConcordServer(PostgreSQLContainer<?> db) {
        this(db, TestingServerConfig.createDefault());
    }

    @Deprecated
    public TestingConcordServer(PostgreSQLContainer<?> db, int apiPort, Map<String, String> extraConfiguration, List<Function<Config, Module>> extraModules) {
        this(db, new TestingServerConfig(Optional.of(apiPort), extraConfiguration, extraModules, Optional.empty(), Optional.empty()));
    }

    public TestingConcordServer(PostgreSQLContainer<?> db, TestingServerConfig config) {
        this.db = requireNonNull(db);

        requireNonNull(config);

        this.extraConfiguration = requireNonNull(config.extraConfiguration());
        this.apiPort = config.apiPort().orElse(8001);
        this.extraModules = requireNonNull(config.extraModules());
        this.adminApiKey = config.adminApiToken().orElseGet(() -> randomString(8));
        this.agentApiKey = config.agentApiToken().orElseGet(() -> randomString(8));
    }

    public synchronized TestingConcordServer start() throws Exception {
        checkArgument(db.isRunning(), "The database container is not running");

        var config = prepareConfig(db);
        var system = new ConcordServerModule(config);
        var allModules = Stream.concat(extraModules.stream().map(f -> f.apply(config)), Stream.of(system)).toList();
        server = ConcordServer.withModules(allModules)
                .start();

        return this;
    }

    public synchronized void stop() throws Exception {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Override
    public void close() throws Exception {
        this.stop();
    }

    public int getApiPort() {
        return apiPort;
    }

    public String getApiBaseUrl() {
        return "http://localhost:" + apiPort;
    }

    public ConcordServer getServer() {
        return server;
    }

    public PostgreSQLContainer<?> getDb() {
        return db;
    }

    public String getAdminApiKey() {
        return adminApiKey;
    }

    public String getAgentApiKey() {
        return agentApiKey;
    }

    private Config prepareConfig(PostgreSQLContainer<?> db) {
        var extraConfig = ConfigFactory.parseMap(this.extraConfiguration);

        var testConfig = ConfigFactory.parseMap(ImmutableMap.<String, String>builder()
                .put("server.port", String.valueOf(apiPort))
                .put("db.url", db.getJdbcUrl())
                .put("db.appUsername", db.getUsername())
                .put("db.appPassword", db.getPassword())
                .put("db.inventoryUsername", db.getUsername())
                .put("db.inventoryPassword", db.getPassword())
                .put("db.changeLogParameters.defaultAdminToken", adminApiKey)
                .put("db.changeLogParameters.defaultAgentToken", agentApiKey)
                .put("secretStore.serverPassword", randomString(64))
                .put("secretStore.secretStoreSalt", randomString(64))
                .put("secretStore.projectSecretSalt", randomString(64))
                .build());

        var defaultConfig = ConfigFactory.load("concord-server.conf", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults().setAllowUnresolved(true))
                .getConfig("concord-server");

        return extraConfig.withFallback(testConfig.withFallback(defaultConfig)).resolve();
    }

    private static String randomString(int minLength) {
        byte[] ab = new byte[minLength];
        new SecureRandom().nextBytes(ab);
        return Base64.getEncoder().encodeToString(ab);
    }

    /**
     * Just an example.
     */
    public static void main(String[] args) throws Exception {
        var adminApiKey = Optional.ofNullable(System.getenv("TEST_ADMIN_API_KEY"));

        var serverConfig = TestingServerConfig.createDefault()
                .withOptionalAdminApiKey(adminApiKey)
                .withExtraConfiguration(Map.of("process.watchdogPeriod", "10 seconds"));

        try (var db = new PostgreSQLContainer<>("postgres:15-alpine");
             var server = new TestingConcordServer(db, serverConfig)) {
            db.start();
            server.start();
            System.out.printf("""
                            ==============================================================
                            
                              UI: http://localhost:8001/
                              DB:
                                JDBC URL: %s
                                username: %s
                                password: %s
                            
                              admin API key: %s
                              agent API key: %s
                            %n""", db.getJdbcUrl(),
                    db.getUsername(),
                    db.getPassword(),
                    server.getAdminApiKey(),
                    server.getAgentApiKey());

            Thread.currentThread().

                    join();
        }
    }
}

