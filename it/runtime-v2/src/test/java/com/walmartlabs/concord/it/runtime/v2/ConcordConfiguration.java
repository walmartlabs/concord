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

import ca.ibodrov.concord.testcontainers.junit4.ConcordRule;
import org.testcontainers.images.PullPolicy;

public final class ConcordConfiguration {

    public static ConcordRule configure() {
        ConcordRule concord = new ConcordRule()
                .pathToRunnerV1(null)
                .pathToRunnerV2("target/runner-v2.jar")
                .dbImage(System.getProperty("db.image", "library/postgres:10"))
                .serverImage(System.getProperty("server.image", "walmartlabs/concord-server"))
                .agentImage(System.getProperty("agent.image", "walmartlabs/concord-agent"))
                .pullPolicy(PullPolicy.defaultPolicy())
                .streamServerLogs(true)
                .streamAgentLogs(true)
                .useLocalMavenRepository(true);

        boolean localMode = Boolean.parseBoolean(System.getProperty("it.local.mode"));
        if (localMode) {
            concord.mode(ConcordRule.Mode.LOCAL);
        } else {
            boolean remoteMode = Boolean.parseBoolean(System.getProperty("it.remote.mode"));
            if (remoteMode) {
                concord.mode(ConcordRule.Mode.REMOTE);
                concord.apiToken(System.getProperty("it.remote.token"));
            }
        }

        return concord;
    }

    private ConcordConfiguration() {
    }
}
