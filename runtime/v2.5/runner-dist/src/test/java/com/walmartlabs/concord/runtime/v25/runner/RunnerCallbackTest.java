package com.walmartlabs.concord.runtime.v25.runner;

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

import com.sun.net.httpserver.HttpServer;
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.runtime.common.cfg.ApiConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.LoggingConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.model.EventConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v25.runner.engine.LifecycleEvent;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
class RunnerCallbackTest {

    @Test
    void persistsUndeliverableEventBatchesAsBoundedAttachments(@TempDir Path workDirectory) throws Exception {
        var server = failingServer();
        server.start();
        try {
            var callback = callback(server, workDirectory);
            try {
                assertDoesNotThrow(() -> callback.onEvent(event("first")));

                var attachments = workDirectory.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME);
                Path persisted;
                try (var files = Files.list(attachments)) {
                    persisted = files
                            .filter(path -> path.getFileName().toString().matches("invalid_event_[0-9a-f]+\\.json"))
                            .findFirst()
                            .orElseThrow();
                }
                assertTrue(Files.readString(persisted).contains("STEP_STARTED"));

                for (var i = 0; i < 99; i++) {
                    Files.writeString(attachments.resolve("invalid_event_preexisting_" + i + ".json"), "[]");
                }
                assertEquals(100, invalidEventFileCount(attachments));

                assertDoesNotThrow(() -> callback.onEvent(event("after-limit")));
                assertEquals(100, invalidEventFileCount(attachments));
            } finally {
                callback.close();
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void masksLongRegisteredSecretsBeforeTruncatingTaskEvents(@TempDir Path workDirectory) throws Exception {
        var requests = new CopyOnWriteArrayList<String>();
        var server = recordingServer(requests);
        var secret = "prefix-" + "s".repeat(100) + "-suffix";
        var sensitiveData = new RunnerWiring.SensitiveDataRegistry();
        sensitiveData.add(secret);
        server.start();
        try {
            var configuration = RunnerConfiguration.builder()
                    .api(ApiConfiguration.builder().retryCount(0).retryInterval(0).build())
                    .logging(LoggingConfiguration.builder().sendSystemOutAndErrToSLF4J(false).build())
                    .build();
            var process = ProcessConfiguration.builder()
                    .instanceId(UUID.randomUUID())
                    .events(EventConfiguration.builder()
                            .batchSize(1)
                            .batchFlushInterval(3600)
                            .recordTaskOutVars(true)
                            .truncateOutVars(true)
                            .truncateMaxStringLength(10)
                            .build())
                    .build();
            var client = new ApiClient(HttpClient.newHttpClient())
                    .setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var callback = new RunnerCallback(configuration, process, client, sensitiveData,
                    new RunnerLogging(configuration, client, process.instanceId(), sensitiveData, workDirectory), workDirectory);
            try {
                var invocation = new com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime.Invocation(
                        "test", "execute", List.of(), List.of(), null);

                callback.onTask(invocation, Map.of("value", secret), null);

                assertEquals(1, requests.size());
                assertTrue(requests.getFirst().contains("******"));
                assertFalse(requests.getFirst().contains("prefix-"));
                assertFalse(requests.getFirst().contains("-suffix"));
            } finally {
                callback.close();
            }
        } finally {
            server.stop(0);
        }
    }

    private static RunnerCallback callback(HttpServer server, Path workDirectory) {
        var configuration = RunnerConfiguration.builder()
                .api(ApiConfiguration.builder().retryCount(0).retryInterval(0).build())
                .logging(LoggingConfiguration.builder().sendSystemOutAndErrToSLF4J(false).build())
                .build();
        var process = ProcessConfiguration.builder()
                .instanceId(UUID.randomUUID())
                .events(EventConfiguration.builder().batchSize(1).batchFlushInterval(3600).build())
                .build();
        var client = new ApiClient(HttpClient.newHttpClient())
                .setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return new RunnerCallback(configuration, process, client, new RunnerWiring.SensitiveDataRegistry(),
                new RunnerLogging(configuration, client, process.instanceId(), new RunnerWiring.SensitiveDataRegistry(),
                        workDirectory), workDirectory);
    }

    private static HttpServer failingServer() throws java.io.IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var response = "failed".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        return server;
    }

    private static HttpServer recordingServer(List<String> requests) throws java.io.IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        return server;
    }

    private static LifecycleEvent event(String name) {
        return new LifecycleEvent(LifecycleEvent.Type.STEP_STARTED, name, null, 1,
                "flow.yaml", 1, 1, "default." + name, Map.of());
    }

    private static long invalidEventFileCount(Path attachments) throws java.io.IOException {
        try (var files = Files.list(attachments)) {
            return files.filter(path -> path.getFileName().toString().startsWith("invalid_event_")).count();
        }
    }
}
