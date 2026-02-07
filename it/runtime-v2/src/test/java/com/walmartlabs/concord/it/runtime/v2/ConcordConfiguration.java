package com.walmartlabs.concord.it.runtime.v2;

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

import java.nio.file.Path;

public final class ConcordConfiguration {

    private static final Path sharedDir = BaseConcordConfiguration.setupSharedDir("concord-it");

    public static Path sharedDir() {
        return sharedDir;
    }

    public static ConcordRule configure() {
        BaseConcordConfiguration.writeSigningKey(sharedDir);

        ConcordRule concord = BaseConcordConfiguration.createBase()
                .pathToRunnerV1(null)
                .pathToRunnerV2("target/runner-v2.jar")
                .sharedContainerDir(sharedDir)
                .extraConfigurationSupplier(() -> BaseConcordConfiguration.baseConfig() + """
                    concord-server {
                        process {
                            signingKeyPath = "%%sharedDir%%/signing.pem"
                        }
                    }
                    """.replaceAll("%%sharedDir%%", sharedDir().toString()));

        return BaseConcordConfiguration.applyMode(concord);
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
