package com.walmartlabs.concord.it.common;

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
import org.testcontainers.images.PullPolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class BaseConcordConfiguration {

    public static ConcordRule createBase() {
        return new ConcordRule()
                .dbImage(System.getProperty("db.image", "library/postgres:10"))
                .serverImage(System.getProperty("server.image", "walmartlabs/concord-server"))
                .agentImage(System.getProperty("agent.image", "walmartlabs/concord-agent"))
                .pullPolicy(PullPolicy.defaultPolicy())
                .streamServerLogs(true)
                .streamAgentLogs(true)
                .useLocalMavenRepository(true);
    }

    public static Path setupSharedDir(String name) {
        Path dir = Paths.get(System.getProperty("java.io.tmpdir")).resolve(name);
        if (Files.notExists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return dir;
    }

    public static void writeSigningKey(Path dir) {
        try {
            Files.writeString(dir.resolve("signing.pem"), generatePkcs8PemPrivateKey());
        } catch (Exception e) {
            throw new IllegalStateException("Error writing server username signing key.", e);
        }
    }

    public static ConcordRule applyMode(ConcordRule concord) {
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

    public static String baseConfig() {
        return """
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
                """;
    }

    private static String generatePkcs8PemPrivateKey() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair pair = keyGen.generateKeyPair();
        byte[] pkcs8 = pair.getPrivate().getEncoded();
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pkcs8);
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }

    private BaseConcordConfiguration() {
    }
}
