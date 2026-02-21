package com.walmartlabs.concord.it.runtime.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import org.testcontainers.images.PullPolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ConcordConfiguration {

    private static final Path sharedDir = Paths.get(System.getProperty("java.io.tmpdir")).resolve("concord-it");

    public static Path sharedDir() {
        return sharedDir;
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
        ConcordRule concord = new ConcordRule()
                .pathToRunnerV1(null)
                .pathToRunnerV2("target/runner-v2.jar")
                .dbImage(System.getProperty("db.image", "library/postgres:14"))
                .serverImage(System.getProperty("server.image", "walmartlabs/concord-server"))
                .agentImage(System.getProperty("agent.image", "walmartlabs/concord-agent"))
                .pullPolicy(PullPolicy.defaultPolicy())
                .streamServerLogs(true)
                .streamAgentLogs(true)
                .sharedContainerDir(sharedDir)
                .useLocalMavenRepository(true)
                .extraConfigurationSupplier(() -> """
                    concord-server {
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
                        prefork {
                            enabled = true
                        }
                    }
                    """);

        boolean localMode = Boolean.parseBoolean(System.getProperty("it.local.mode"));
        if (localMode) {
            concord.mode(ConcordRule.Mode.LOCAL);
        } else {
            boolean remoteMode = Boolean.parseBoolean(System.getProperty("it.remote.mode"));
            if (remoteMode) {
                concord.mode(ConcordRule.Mode.REMOTE);
                concord.apiToken(System.getProperty("it.remote.token"));
                concord.apiBaseUrl(System.getProperty("it.remote.baseUrl"));
            }
        }

        return concord;
    }

    // TODO: move to testcontainers
    public static String getServerUrlForAgent(ConcordRule concord) {
        switch (concord.mode()) {
            case LOCAL:
                return "http://localhost:8001";
            case REMOTE:
                return System.getProperty("it.remote.baseUrl");
            case DOCKER:
                return "http://server:8001";
            default:
                throw new IllegalArgumentException("Unknown mode: " + concord.mode());
        }
    }

    private ConcordConfiguration() {
    }
}
