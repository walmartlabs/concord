package com.walmartlabs.concord.github.appinstallation.cfg;

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

import com.typesafe.config.ConfigFactory;
import com.walmartlabs.concord.common.cfg.MappingAuthConfig;
import com.walmartlabs.concord.github.appinstallation.GitHubAppAuthConfigNew;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static com.walmartlabs.concord.github.appinstallation.TestConstants.PRIVATE_KEY_TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigTest {

    @TempDir
    private static Path workDir;

    @Test
    void simpleConfig() throws Exception {
        var pk = Files.writeString(workDir.resolve("pk.pem"), PRIVATE_KEY_TEXT);
        var typesafeConfig = ConfigFactory.parseString("""
                {
                    "auth" = [
                        { type = "GITHUB_APP_INSTALLATION", urlPattern = "(?<baseUrl>github.local)", clientId = "123", privateKey = "{{PK_PATH}}" },
                        { type = "OAUTH_TOKEN", urlPattern = "(?<baseUrl>github.local)", token = "mock-token" }
                    ]
                }""".replace("{{PK_PATH}}", pk.toString()));
        var cfg = GitHubAppInstallationConfig.fromConfig(typesafeConfig);
        assertNotNull(cfg);
        assertEquals(10240, cfg.getSystemAuthCacheMaxWeight());
        assertEquals(Duration.ofSeconds(30), cfg.getHttpClientTimeout());
        assertEquals(Duration.ofMinutes(50), cfg.getSystemAuthCacheDuration());
        assertEquals(2, cfg.getAuthConfigs().size());

        var appInstall = assertInstanceOf(GitHubAppAuthConfigNew.class, cfg.getAuthConfigs().get(0));
        assertEquals("x-access-token", appInstall.username());
        assertEquals("https://api.github.com", appInstall.apiUrl());

        var oauth = assertInstanceOf(MappingAuthConfig.OauthAuthConfig.class, cfg.getAuthConfigs().get(1));
        assertNull(oauth.username());
        assertEquals("mock-token", oauth.token());
    }

    @Test
    void overrideConfig() throws Exception {
        var pk = Files.writeString(workDir.resolve("pk.pem"), PRIVATE_KEY_TEXT);
        var typesafeConfig = ConfigFactory.parseString("""
                {
                    httpClientTimeout = "1 minute",
                    systemAuthCacheDuration = "1 minute",
                    systemAuthCacheMaxWeight = "10"
                    "auth" = [
                        { type = "GITHUB_APP_INSTALLATION", urlPattern = "(?<baseUrl>github.local)", username = "custom", apiUrl = "https://api.github.local", clientId = "123", privateKey = "{{PK_PATH}}" },
                        { type = "OAUTH_TOKEN", urlPattern = "(?<baseUrl>github.local)", token = "mock-token", username = "custom" }
                    ]
                }""".replace("{{PK_PATH}}", pk.toString()));
        var cfg = GitHubAppInstallationConfig.fromConfig(typesafeConfig);
        assertNotNull(cfg);
        assertEquals(10, cfg.getSystemAuthCacheMaxWeight());
        assertEquals(Duration.ofMinutes(1), cfg.getHttpClientTimeout());
        assertEquals(Duration.ofMinutes(1), cfg.getSystemAuthCacheDuration());
        assertEquals(2, cfg.getAuthConfigs().size());

        var appInstall = assertInstanceOf(GitHubAppAuthConfigNew.class, cfg.getAuthConfigs().get(0));
        assertEquals("custom", appInstall.username());
        assertEquals("https://api.github.local", appInstall.apiUrl());

        var oauth = assertInstanceOf(MappingAuthConfig.OauthAuthConfig.class, cfg.getAuthConfigs().get(1));
        assertEquals("custom", oauth.username());
        assertEquals("mock-token", oauth.token());
    }
}
