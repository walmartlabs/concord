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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

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

        writePrivateKey(sharedDir.resolve("signing.pem"));
    }

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
                        process {
                            signingKeyPath = "%%sharedDir%%/signing.pem"
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
                    """.replaceAll("%%sharedDir%%", sharedDir().toString()));

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
        return switch (concord.mode()) {
            case LOCAL -> "http://localhost:8001";
            case REMOTE -> System.getProperty("it.remote.baseUrl");
            case DOCKER -> "http://server:8001";
        };
    }

    private static Path writePrivateKey(Path targetFile) {
        try {
            return Files.writeString(targetFile, generatePkcs8PemPrivateKey());
        } catch (Exception e) {
            throw new IllegalStateException("Error writing server username signing key.", e);
        }
    }

    private static String generatePkcs8PemPrivateKey() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair pair = keyGen.generateKeyPair();
        byte[] pkcs8 = pair.getPrivate().getEncoded();
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pkcs8);
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }

    private ConcordConfiguration() {
    }
}
