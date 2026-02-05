package com.walmartlabs.concord.it.runtime.v1;

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

public final class ConcordConfiguration {

    public static ConcordRule configure() {
        ConcordRule concord = new ConcordRule()
                .pathToRunnerV1("target/runner-v1.jar")
                .pathToRunnerV2(null)
                .dbImage(System.getProperty("db.image", "library/postgres:10"))
                .serverImage(System.getProperty("server.image", "walmartlabs/concord-server"))
                .agentImage(System.getProperty("agent.image", "walmartlabs/concord-agent"))
                .pullPolicy(PullPolicy.defaultPolicy())
                .streamServerLogs(true)
                .streamAgentLogs(true)
                .useLocalMavenRepository(true)
                .extraConfigurationSupplier(() -> """
                        concord-server {
                            db {
                                maxPoolSize = 30
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
                            prefork {
                                enabled = true
                            }
                        }
                        """);

        boolean localMode = Boolean.parseBoolean(System.getProperty("it.local.mode"));
        if (localMode) {
            concord.mode(ConcordRule.Mode.LOCAL);
        } else if (Boolean.parseBoolean(System.getProperty("it.remote.mode"))) {
            concord.mode(ConcordRule.Mode.REMOTE);
            concord.apiToken(System.getProperty("it.remote.token"));
            concord.apiBaseUrl(System.getProperty("it.remote.baseUrl"));
        } else {
            concord.mode(ConcordRule.Mode.DOCKER);
        }

        return concord;
    }

    private ConcordConfiguration() {
    }
}
