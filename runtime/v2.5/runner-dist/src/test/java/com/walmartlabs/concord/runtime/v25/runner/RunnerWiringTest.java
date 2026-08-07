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

import com.walmartlabs.concord.client.v2.ConcordTaskV2;
import com.walmartlabs.concord.plugins.mock.MockTask;
import com.walmartlabs.concord.plugins.mock.MockTaskProvider;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RunnerWiringTest {

    @Test
    void ignoresBlankSensitiveValues() {
        var values = new RunnerWiring.SensitiveDataRegistry();

        values.add(" ");

        assertEquals(java.util.Set.of(), values.get());
    }

    @Test
    void createsConcordTaskWithRunnerApiClientFactoryBinding(@TempDir Path workDirectory) throws Exception {
        var runtime = runtime(workDirectory);

        var task = runtime.taskProviders().stream()
                .map(provider -> provider.createTask(context(Map.of()), "concord"))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();

        assertInstanceOf(ConcordTaskV2.class, task);
    }

    @Test
    void injectsDiscoveredTasksIntoMockProvider(@TempDir Path workDirectory) throws Exception {
        var runtime = runtime(workDirectory);
        var mockProvider = runtime.taskProviders().stream()
                .filter(MockTaskProvider.class::isInstance)
                .map(MockTaskProvider.class::cast)
                .findFirst()
                .orElseThrow();
        var context = context(Map.of("mocks", List.of(Map.of("task", "discovered-only", "out", Map.of()))));

        var task = mockProvider.createTask(context, "discovered-only");

        assertInstanceOf(MockTask.class, task);
    }

    private static RunnerWiring.Runtime runtime(Path workDirectory) throws Exception {
        Files.writeString(workDirectory.resolve(Constants.Files.INSTANCE_ID_FILE_NAME), UUID.randomUUID().toString());
        Files.writeString(workDirectory.resolve(Constants.Files.CONFIGURATION_FILE_NAME),
                "{\"processInfo\":{\"sessionToken\":\"test-token\"}}");
        var previous = System.getProperty("user.dir");
        System.setProperty("user.dir", workDirectory.toString());
        try {
            return RunnerWiring.create(RunnerConfiguration.builder().build());
        } finally {
            System.setProperty("user.dir", previous);
        }
    }

    private static Context context(Map<String, Object> values) {
        var variables = new MapBackedVariables(values);
        return (Context) Proxy.newProxyInstance(Context.class.getClassLoader(), new Class<?>[]{Context.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "variables", "defaultVariables" -> variables;
                    case "processConfiguration" -> com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration.builder()
                            .build();
                    case "toString" -> "test-context";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
