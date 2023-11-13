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
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class TestingConcordServer implements AutoCloseable {

    private PostgreSQLContainer<?> db;
    private ConcordServer server;
    private Map<String, String> extraConfiguration;
    private List<Function<Config, Module>> extraModules;

    public TestingConcordServer(Map<String, String> extraConfiguration, List<Function<Config, Module>> extraModules) {
        this.extraConfiguration = requireNonNull(extraConfiguration);
        this.extraModules = requireNonNull(extraModules);
    }

    public TestingConcordServer() {
        this(Map.of(), List.of());
    }

    public synchronized TestingConcordServer start() throws Exception {
        db = new PostgreSQLContainer<>("postgres:15-alpine");
        db.start();

        var config = prepareConfig(db);
        var system = new ConcordServerModule(config);
        var allModules = Stream.concat(extraModules.stream().map(f -> f.apply(config)), Stream.of(system)).toList();
        server = ConcordServer.withModules(allModules)
                .start();

        return this;
    }

    @Override
    public synchronized void close() throws Exception {
        this.stop();
    }

    public void stop() throws Exception {
        if (server != null) {
            server.stop();
            server = null;
        }

        if (db != null) {
            db.stop();
            db = null;
        }
    }

    public PostgreSQLContainer<?> getDb() {
        return db;
    }

    private Config prepareConfig(PostgreSQLContainer<?> db) {
        var extraConfig = ConfigFactory.parseMap(this.extraConfiguration);

        var testConfig = ConfigFactory.parseMap(Map.of(
                "db.url", db.getJdbcUrl(),
                "db.appUsername", db.getUsername(),
                "db.appPassword", db.getPassword(),
                "db.inventoryUsername", db.getUsername(),
                "db.inventoryPassword", db.getPassword(),
                "db.changeLogParameters.defaultAdminToken", "foobar",
                "secretStore.serverPassword", randomString(),
                "secretStore.secretStoreSalt", randomString(),
                "secretStore.projectSecretSalt", randomString()
        ));

        var defaultConfig = ConfigFactory.load("concord-server.conf", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults().setAllowUnresolved(true))
                .getConfig("concord-server");

        return extraConfig.withFallback(testConfig.withFallback(defaultConfig)).resolve();
    }

    private static String randomString() {
        byte[] ab = new byte[64];
        new SecureRandom().nextBytes(ab);
        return Base64.getEncoder().encodeToString(ab);
    }

    public static void main(String[] args) throws Exception {
        try (TestingConcordServer server = new TestingConcordServer(Map.of("process.watchdogPeriod", "10 seconds"), List.of())) {
            server.start();
            Thread.sleep(100000);
        }
    }
}
