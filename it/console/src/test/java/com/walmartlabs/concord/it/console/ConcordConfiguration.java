package com.walmartlabs.concord.it.console;

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

import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.PullPolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class ConcordConfiguration {

    private static final Path sharedDir = Paths.get(System.getProperty("java.io.tmpdir")).resolve("concord-console-it");
    private static volatile GenericContainer<?> seleniumContainer;

    public static Path sharedDir() {
        return sharedDir;
    }

    public static int seleniumPort() {
        return seleniumContainer.getMappedPort(4444);
    }

    public static String consoleBaseUrl() {
        return "http://server:8001";
    }

    static {
        if (Files.notExists(sharedDir)) {
            try {
                Files.createDirectories(sharedDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static ConcordRule configure() {
        return new ConcordRule()
                .pathToRunnerV1(null)
                .pathToRunnerV2(null)
                .dbImage(System.getProperty("db.image", "library/postgres:10"))
                .serverImage(System.getProperty("server.image", "walmartlabs/concord-server"))
                .agentImage(System.getProperty("agent.image", "walmartlabs/concord-agent"))
                .pullPolicy(PullPolicy.defaultPolicy())
                .streamServerLogs(true)
                .streamAgentLogs(true)
                .sharedContainerDir(sharedDir)
                .useLocalMavenRepository(true)
                .extraContainerSupplier(network -> {
                    @SuppressWarnings("resource")
                    GenericContainer<?> selenium = new GenericContainer<>(System.getProperty("selenium.image", "seleniarm/standalone-chromium:110.0"))
                            .withNetwork(network)
                            .withNetworkAliases("selenium")
                            .withExposedPorts(4444)
                            .withSharedMemorySize(2147483648L)
                            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*Started Selenium Standalone.*", 1));
                    seleniumContainer = selenium;
                    return List.of(selenium);
                })
                .extraConfigurationSupplier(() -> """
                    concord-server {
                        secretStore {
                            serverPassword = "aXRpdGl0"
                            secretStoreSalt = "aXRpdGl0"
                            projectSecretSalt = "aXRpdGl0"
                        }
                        queue {
                            enqueuePollInterval = "250 milliseconds"
                            dispatcher {
                                pollDelay = "250 milliseconds"
                            }
                        }
                    }
                    concord-agent {
                        dependencyResolveTimeout = "30 seconds"
                        logMaxDelay = "250 milliseconds"
                        pollInterval = "250 milliseconds"
                    }
                    """);
    }

    private ConcordConfiguration() {
    }
}
