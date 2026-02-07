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
import com.walmartlabs.concord.it.common.BaseConcordConfiguration;
import org.testcontainers.containers.GenericContainer;

import java.nio.file.Path;
import java.util.List;

public final class ConcordConfiguration {

    private static final Path sharedDir = BaseConcordConfiguration.setupSharedDir("concord-console-it");
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

    public static ConcordRule configure() {
        return BaseConcordConfiguration.createBase()
                .pathToRunnerV1(null)
                .pathToRunnerV2(null)
                .sharedContainerDir(sharedDir)
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
                .extraConfigurationSupplier(() -> BaseConcordConfiguration.baseConfig() + """
                    concord-server {
                        secretStore {
                            serverPassword = "aXRpdGl0"
                            secretStoreSalt = "aXRpdGl0"
                            projectSecretSalt = "aXRpdGl0"
                        }
                    }
                    """);
    }

    private ConcordConfiguration() {
    }
}
