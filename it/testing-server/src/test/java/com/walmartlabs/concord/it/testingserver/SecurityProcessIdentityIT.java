package com.walmartlabs.concord.it.testingserver;

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

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.DefaultApiClientFactory;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.server.plugins.pfedsso.SsoToken;
import com.walmartlabs.concord.server.process.ExecutionIdentity;
import com.walmartlabs.concord.server.process.ExecutionIdentityCodec;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.process.SessionTokenCreator;
import com.walmartlabs.concord.server.process.state.ProcessCheckpointManager;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SecurityProcessIdentityIT {
    @TempDir Path temp;

    @Test
    @Timeout(180)
    void forkStoresCurrentActorForSessionAuthentication() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:15-alpine")) {
            db.start();
            int port;
            try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
            var extra = Map.<String, Object>of(
                    "websockets.requirePermission", false,
                    "secretStore.serverPassword", secret(),
                    "secretStore.secretStoreSalt", secret(),
                    "secretStore.projectSecretSalt", secret());
            try (var server = new TestingConcordServer(db, port, extra, List.of())) {
                server.start();
                var client = new DefaultApiClientFactory(server.getApiBaseUrl()).create(
                        ApiClientConfiguration.builder().apiKey(server.getAdminApiKey()).build());
                var parent = new ProcessApi(client).startProcess(Map.of(
                        "startAt", OffsetDateTime.now().plusDays(1).toString(),
                        "concord.yml", """
                        configuration:
                          runtime: concord-v2
                        flows:
                          default:
                            - log: hello
                        """.getBytes(StandardCharsets.UTF_8)));
                var injector = server.getServer().getInjector();
                var processes = injector.getInstance(ProcessManager.class);
                var entry = processes.assertProcess(parent.getInstanceId());
                long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
                while (entry.status() != ProcessStatus.ENQUEUED && System.nanoTime() < deadline) {
                    assertNotEquals(ProcessStatus.FAILED, entry.status());
                    Thread.sleep(200);
                    entry = processes.assertProcess(parent.getInstanceId());
                }
                assertEquals(ProcessStatus.ENQUEUED, entry.status());
                var parentKey = new ProcessKey(entry.instanceId(), entry.createdAt());
                var states = injector.getInstance(ProcessStateManager.class);
                var actor = states.get(parentKey, ".concord/current_user", SecurityProcessIdentityIT::json).orElseThrow();

                var http = HttpClient.newHttpClient();
                var fork = http.send(HttpRequest.newBuilder(URI.create(server.getApiBaseUrl()
                                + "/api/v1/process/" + parent.getInstanceId() + "/fork"))
                        .header("Authorization", server.getAdminApiKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, fork.statusCode(), fork.body());
                UUID childId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(fork.body()).get("instanceId").asText());
                var child = processes.assertProcess(childId);
                var childKey = new ProcessKey(child.instanceId(), child.createdAt());
                assertEquals(actor, states.get(childKey, ".concord/current_user", SecurityProcessIdentityIT::json).orElseThrow());

                String token = injector.getInstance(SessionTokenCreator.class).create(childKey);
                var response = http.send(HttpRequest.newBuilder(URI.create(server.getApiBaseUrl()
                                + "/api/v1/process/" + childId))
                        .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((token + ":").getBytes(StandardCharsets.UTF_8)))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), response.body());
            }
        }
    }

    @Test
    @Timeout(180)
    void migratesBeforePluginRemovalAndRestoresOldCheckpoint() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:15-alpine")) {
            db.start();
            int port;
            try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
            var extra = Map.<String, Object>of(
                    "websockets.requirePermission", false,
                    "secretStore.serverPassword", secret(),
                    "secretStore.secretStoreSalt", secret(),
                    "secretStore.projectSecretSalt", secret());
            try (var server = new TestingConcordServer(db, port, extra, List.of())) {
                server.start();
                var client = new DefaultApiClientFactory(server.getApiBaseUrl()).create(
                        ApiClientConfiguration.builder().apiKey(server.getAdminApiKey()).build());
                var process = new ProcessApi(client).startProcess(Map.of(
                        "startAt", OffsetDateTime.now().plusDays(1).toString(),
                        "concord.yml", """
                        configuration:
                          runtime: concord-v2
                        flows:
                          default:
                            - log: hello
                        """.getBytes(StandardCharsets.UTF_8)));
                var processes = server.getServer().getInjector().getInstance(ProcessManager.class);
                var entry = processes.assertProcess(process.getInstanceId());
                long enqueueDeadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
                while (entry.status() != ProcessStatus.ENQUEUED && System.nanoTime() < enqueueDeadline) {
                    assertNotEquals(ProcessStatus.FAILED, entry.status(), "Process preparation failed");
                    Thread.sleep(200);
                    entry = processes.assertProcess(process.getInstanceId());
                }
                assertEquals(ProcessStatus.ENQUEUED, entry.status(), "Process must finish preparation before seeding legacy state");
                assertNotNull(entry.startAt(), "Process must remain scheduled while legacy state is seeded");
                var key = new ProcessKey(entry.instanceId(), entry.createdAt());
                var states = server.getServer().getInjector().getInstance(ProcessStateManager.class);
                var initiator = states.getInitial(key, "initiator", SecurityProcessIdentityIT::decode).orElseThrow();
                UUID originalActor;
                try (var connection = java.sql.DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                     var statement = connection.prepareStatement("select user_id from users where username = 'cron'");
                     var rows = statement.executeQuery()) {
                    assertTrue(rows.next(), "Built-in cron user must exist");
                    originalActor = rows.getObject(1, UUID.class);
                }
                assertNotEquals(initiator.userId(), originalActor);
                byte[] oldInitial = legacy(originalActor, "local");
                byte[] oldCurrent = legacy(initiator.userId(), "sso");
                states.tx(tx -> states.updateStateData(tx, ProcessStateManager.StateTable.INITIAL,
                        new ProcessStateManager.StateRow(key.getCreatedAt(), key.getInstanceId(), "initiator"), ignored -> oldInitial));
                states.replace(key, ".concord/current_user", oldCurrent);
                UUID checkpointId = UUID.randomUUID();
                Path archive = temp.resolve("old-checkpoint.zip");
                try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                    zip.putNextEntry(new ZipEntry(".concord/current_user"));
                    zip.write(oldCurrent);
                    zip.closeEntry();
                    zip.putNextEntry(new ZipEntry(".concord/restored.txt"));
                    zip.write("checkpoint-data".getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
                server.getServer().getInjector().getInstance(ProcessCheckpointManager.class)
                        .importCheckpoint(key, checkpointId, UUID.randomUUID(), "old", archive);
                server.stop();

                // The same database is restarted with the old plugin classes available for conversion.
                server.start();
                var migratedStates = server.getServer().getInjector().getInstance(ProcessStateManager.class);
                assertEquals(new ExecutionIdentity(originalActor, "local"), migratedStates.getInitial(key, "initiator", SecurityProcessIdentityIT::json).orElseThrow());
                assertEquals(new ExecutionIdentity(initiator.userId(), "sso"), migratedStates.get(key, ".concord/current_user", SecurityProcessIdentityIT::json).orElseThrow());
                assertNotNull(server.getServer().getInjector().getInstance(ProcessCheckpointManager.class).restoreCheckpoint(key, checkpointId));
                assertEquals(new ExecutionIdentity(initiator.userId(), "sso"), migratedStates.get(key, ".concord/current_user", SecurityProcessIdentityIT::json).orElseThrow());
                assertTrue(server.getServer().getInjector().getInstance(AuthorizationService.class)
                        .prepare(initiator.userId()).roles().contains(Roles.ADMIN));
                server.stop();
                // The actor's old serialized data must not preserve a revoked grant.
                try (var connection = java.sql.DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                     var statement = connection.prepareStatement("delete from user_roles where user_id=? and role_id in (select role_id from roles where role_name=?)")) {
                    statement.setObject(1, initiator.userId());
                    statement.setString(2, Roles.ADMIN);
                    assertEquals(1, statement.executeUpdate());
                }

                var config = new java.util.HashMap<String, Object>(extra);
                config.put("server.port", port);
                config.put("db.url", db.getJdbcUrl());
                config.put("db.appUsername", db.getUsername());
                config.put("db.appPassword", db.getPassword());
                config.put("db.inventoryUsername", db.getUsername());
                config.put("db.inventoryPassword", db.getPassword());
                config.put("db.changeLogParameters.defaultAdminToken", server.getAdminApiKey());
                config.put("db.changeLogParameters.defaultAgentToken", server.getAgentApiKey());
                Path configFile = temp.resolve("fork.conf");
                Files.writeString(configFile, ConfigFactory.parseMap(config).root().render(ConfigRenderOptions.concise()));
                Path result = temp.resolve("restored");
                String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
                var retained = new ArrayList<String>();
                for (String element : classpath.split(java.io.File.pathSeparator)) {
                    if (!containsPfedClasses(Path.of(element))) {
                        retained.add(element);
                    }
                }
                String filtered = String.join(java.io.File.pathSeparator, retained);
                assertNotEquals(classpath, filtered, "PFed artifact must be removed from the fork classpath");
                Path log = temp.resolve("fork.log");
                var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp", filtered, FilteredServerBootstrap.class.getName(), configFile.toString(),
                        key.getInstanceId().toString(), key.getCreatedAt().toString(), checkpointId.toString(),
                        initiator.userId().toString(), result.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
                try {
                    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
                    long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
                    boolean ready = false;
                    while (System.nanoTime() < deadline && child.isAlive()) {
                        try {
                            var response = http.send(HttpRequest.newBuilder(URI.create(server.getApiBaseUrl() + "/api/v1/server/ping"))
                                    .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.discarding());
                            if (response.statusCode() == 200 && Files.exists(result)) { ready = true; break; }
                        } catch (java.io.IOException ignored) { }
                        Thread.sleep(200);
                    }
                    assertTrue(ready, () -> "Filtered server failed: " + readLog(log));
                    assertEquals("restored", Files.readString(result));
                } finally {
                    child.destroy();
                    if (!child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) child.destroyForcibly().waitFor();
                }
            }
        }
    }

    private static boolean containsPfedClasses(Path path) throws java.io.IOException {
        String pluginPackage = "com/walmartlabs/concord/server/plugins/pfedsso/";
        if (Files.isDirectory(path)) {
            return Files.isDirectory(path.resolve(pluginPackage));
        }
        if (!Files.isRegularFile(path) || !(path.toString().endsWith(".jar") || path.toString().endsWith(".zip"))) {
            return false;
        }
        try (var archive = new ZipFile(path.toFile())) {
            return archive.stream().anyMatch(e -> e.getName().startsWith(pluginPackage) && e.getName().endsWith(".class"));
        }
    }

    private static String secret() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] legacy(UUID id, String provider) throws Exception {
        var principal = new SubjectPrincipal(id, "actor", null, new AuthenticationContext(provider, null, null));
        var token = new SsoToken("actor", "example", "Actor", "actor@example.com", "actor@example.com", "cn=actor", Set.of());
        var out = new ByteArrayOutputStream();
        try (var stream = new ObjectOutputStream(out)) { stream.writeObject(List.of(principal, token)); }
        return out.toByteArray();
    }

    private static Optional<ExecutionIdentity> decode(java.io.InputStream in) {
        try { return Optional.of(ExecutionIdentityCodec.decode(in.readAllBytes())); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static Optional<ExecutionIdentity> json(java.io.InputStream in) {
        try {
            byte[] bytes = in.readAllBytes();
            assertFalse(ExecutionIdentityCodec.isLegacy(bytes));
            return Optional.of(ExecutionIdentityCodec.decode(bytes));
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    private static String readLog(Path path) {
        try { return Files.readString(path); } catch (Exception e) { return e.toString(); }
    }
}
