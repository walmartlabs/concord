package com.walmartlabs.concord.github.appinstallation;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.sdk.Secret;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class TestConstants {
    static final ObjectMapper MAPPPER = new ObjectMapperProvider().get();
    static final String APP_INSTALL_CONTENT = """
            {
                "githubAppInstallation": {
                    "urlPattern": "(?<baseUrl>github.local)/.*",
                    "clientId": "123",
                    "privateKey": "mock-key-data"
                }
            }""";
    static final Secret MOCK_APP_INSTALL_SECRET = new BinaryDataSecret(APP_INSTALL_CONTENT.getBytes(StandardCharsets.UTF_8));
    static final Secret MOCK_STATIC_TOKEN_SECRET = new BinaryDataSecret("mock-static-token".getBytes(StandardCharsets.UTF_8));

    public static final String PRIVATE_KEY_TEXT = generatePrivateKey();

    private static String generatePrivateKey() {
        KeyPairGenerator kpg;
        try {
            kpg = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm not found", e);
        }

        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        Base64.Encoder encoder = Base64.getEncoder();

        return "-----BEGIN PRIVATE KEY-----\n" +
                encoder.encodeToString(kp.getPrivate().getEncoded()) +
                "\n-----END PRIVATE KEY-----\n";
    }
}
