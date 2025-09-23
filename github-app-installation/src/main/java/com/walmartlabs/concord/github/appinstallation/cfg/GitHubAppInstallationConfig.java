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

import com.typesafe.config.Config;
import com.walmartlabs.concord.common.cfg.MappingAuthConfig;
import com.walmartlabs.concord.github.appinstallation.GitHubAppAuthConfig;
import com.walmartlabs.concord.github.appinstallation.exception.GitHubAppException;
import org.immutables.value.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface GitHubAppInstallationConfig {

    List<MappingAuthConfig> getAuthConfigs();

    @Value.Default
    default Duration getSystemAuthCacheDuration() {
        return Duration.ofMinutes(50);
    }

    @Value.Default
    default Duration getHttpClientTimeout() {
        return Duration.ofSeconds(30);
    }

    /**
     * Weight is roughly calculated in kilobytes. Any cached item will have a
     * minimum weight of 1. While further weight calculations are based on size
     * of the given secret, if any.
     * <p/>
     * The default of 10,240 (~10MB) can hold approximately:
     * <ul>
     *     <li>10,000 tokens with no secret</li>
     *     <li>5,000 tokens with string or credentials secret</li>
     *     <li>3,500 tokens with app private key secret</li>
     * </ul>
     */
    @Value.Default
    default long getSystemAuthCacheMaxWeight() {
        return 1024 * 10L;
    }

    static ImmutableGitHubAppInstallationConfig.Builder builder() {
        return ImmutableGitHubAppInstallationConfig.builder();
    }

    static GitHubAppInstallationConfig fromConfig(Config config) {
        var auths = config.getConfigList("auth").stream()
                .map(GitHubAppInstallationConfig::toGitAuth)
                .toList();

        var builder = builder();

        if (config.hasPath("httpClientTimeout")) {
            builder.httpClientTimeout(config.getDuration("httpClientTimeout"));
        }

        if (config.hasPath("systemAuthCacheDuration")) {
            builder.systemAuthCacheDuration(config.getDuration("systemAuthCacheDuration"));
        }

        if (config.hasPath("systemAuthCacheMaxWeight")) {
            builder.systemAuthCacheMaxWeight(config.getInt("systemAuthCacheMaxWeight"));
        }

        return builder
                .authConfigs(auths)
                .build();
    }

    enum AuthSource {
        OAUTH_TOKEN,
        GITHUB_APP_INSTALLATION,
    }

    interface AuthConfig {
        MappingAuthConfig toGitAuth();
    }

    static MappingAuthConfig toGitAuth(com.typesafe.config.Config auth) {
        var a = switch (AuthSource.valueOf(auth.getString("type").toUpperCase())) {
                    case OAUTH_TOKEN -> OauthConfig.from(auth);
                    case GITHUB_APP_INSTALLATION -> AppInstallationConfig.from(auth);
                };
        return a.toGitAuth();
    }

    record OauthConfig(String urlPattern, String username, String token) implements AuthConfig {

        static OauthConfig from(com.typesafe.config.Config cfg) {
            var username = Optional.ofNullable(cfg.hasPath("username") ? cfg.getString("username") : null)
                    .filter(s -> !s.isBlank())
                    .orElse(null);

            return new OauthConfig(
                    cfg.getString("urlPattern"),
                    username,
                    cfg.getString("token")
            );
        }

        @Override
        public MappingAuthConfig toGitAuth() {
            return MappingAuthConfig.OauthAuthConfig.builder()
                    .token(this.token())
                    .username(Optional.ofNullable(this.username()))
                    .urlPattern(MappingAuthConfig.assertBaseUrlPattern(this.urlPattern()))
                    .build();
        }
    }

    record AppInstallationConfig(String urlPattern, String username, String apiUrl, String clientId, String privateKey) implements AuthConfig {

        static AppInstallationConfig from(com.typesafe.config.Config cfg) {
            var username = Optional.ofNullable(cfg.hasPath("username") ? cfg.getString("username") : null)
                    .filter(s -> !s.isBlank())
                    .orElse("x-access-token");

            var apiUrl = Optional.ofNullable(cfg.hasPath("apiUrl") ? cfg.getString("apiUrl") : null)
                    .filter(s -> !s.isBlank())
                    .orElse("https://api.github.com");

            return new AppInstallationConfig(
                    cfg.getString("urlPattern"),
                    username,
                    apiUrl,
                    cfg.getString("clientId"),
                    cfg.getString("privateKey")
            );
        }

        @Override
        public MappingAuthConfig toGitAuth() {
            try {
                var pkData = Files.readString(Paths.get(this.privateKey()));

                return GitHubAppAuthConfig.builder()
                        .urlPattern(MappingAuthConfig.assertBaseUrlPattern(this.urlPattern()))
                        .username(this.username())
                        .clientId(this.clientId())
                        .privateKey(pkData)
                        .apiUrl(this.apiUrl())
                        .build();
            } catch (IOException e) {
                throw new GitHubAppException("Error initializing Git App Installation auth", e);
            }
        }
    }
}
