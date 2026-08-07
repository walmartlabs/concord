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
import com.walmartlabs.concord.runtime.common.cfg.LoggingConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import com.walmartlabs.concord.runtime.v25.runner.engine.LifecycleEvent;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnerLoggingTest {

    @Test
    void masksSensitiveSegmentNameBeforeSendingRequest() throws Exception {
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v2/process", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = "{\"id\":42}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new ApiClient(HttpClient.newHttpClient())
                    .setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var logging = new RunnerLogging(configuration(), client, UUID.randomUUID(), sensitive("top-secret"), Path.of("."));

            logging.onEvent(new LifecycleEvent(LifecycleEvent.Type.STEP_STARTED, "step", null, 1,
                    "flow.yaml", 1, 1, "default.step", Map.of("name", "deploy top-secret")));

            assertTrue(body.get().contains("deploy ******"));
            assertFalse(body.get().contains("top-secret"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void restoresPersistedSegmentRouting() {
        var logging = new RunnerLogging(configuration(), new ApiClient(HttpClient.newHttpClient()), UUID.randomUUID(),
                sensitive(), Path.of("."));
        logging.start();
        var routes = Map.of("definition:1:0:0", 42L);

        logging.restoreSegments(routes);

        var step = new TaskRuntime.StepContext("definition", "definition:1", "flow.yaml", 1, 1,
                Map.of("loopItemIndex", 0, "retryAttempt", 0), null);
        assertEquals(42L, logging.segment(step));
        assertEquals(routes, logging.snapshotSegments());
    }

    private static RunnerConfiguration configuration() {
        return RunnerConfiguration.builder().logging(LoggingConfiguration.builder()
                .sendSystemOutAndErrToSLF4J(false)
                .build()).build();
    }

    private static SensitiveDataHolder sensitive(String... values) {
        return new SensitiveValues(Set.of(values));
    }

    private static final class SensitiveValues implements SensitiveDataHolder {
        private final Set<String> values;

        private SensitiveValues(Set<String> values) {
            this.values = new LinkedHashSet<>(values);
        }

        @Override
        public Set<String> get() {
            return values;
        }

        @Override
        public void add(String sensitiveData) {
            values.add(sensitiveData);
        }

        @Override
        public void addAll(java.util.Collection<String> sensitiveData) {
            values.addAll(sensitiveData);
        }
    }
}
