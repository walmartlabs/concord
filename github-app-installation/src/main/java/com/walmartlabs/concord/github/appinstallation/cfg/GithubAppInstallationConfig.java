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
import com.walmartlabs.concord.common.cfg.ExternalTokenAuth;
import com.walmartlabs.concord.github.appinstallation.AppInstallationAuth;
import com.walmartlabs.concord.github.appinstallation.GitHubAppException;
import org.immutables.value.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface GithubAppInstallationConfig {

    List<ExternalTokenAuth> getAuthConfigs();

    @Value.Default
    default Duration getSystemAuthCacheDuration() {
        return Duration.ofMinutes(50);
    }

    @Value.Default
    default int getSystemAuthCacheMaxSize() {
        return 1_000;
    }

    static ImmutableGithubAppInstallationConfig.Builder builder() {
        return ImmutableGithubAppInstallationConfig.builder();
    }

    static GithubAppInstallationConfig fromConfig(Config config) {
        var auths = config.getConfigList("auth").stream()
                .map(GithubAppInstallationConfig::toGitAuth)
                .toList();

        var builder = builder();

        if (config.hasPath("systemAuthCacheDuration")) {
            builder.systemAuthCacheDuration(config.getDuration("systemAuthCacheDuration"));
        }

        if (config.hasPath("systemAuthCacheMaxSize")) {
            builder.systemAuthCacheMaxSize(config.getInt("systemAuthCacheMaxSize"));
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
        ExternalTokenAuth toGitAuth();
    }

    static ExternalTokenAuth toGitAuth(com.typesafe.config.Config auth) {
        var a = switch (AuthSource.valueOf(auth.getString("type").toUpperCase())) {
                    case OAUTH_TOKEN -> OauthConfig.from(auth);
                    case GITHUB_APP_INSTALLATION -> AppInstallationConfig.from(auth);
                };
        return a.toGitAuth();
    }

    record OauthConfig(String urlPattern, String token) implements AuthConfig {

        static OauthConfig from(com.typesafe.config.Config cfg) {
            return new OauthConfig(
                    cfg.getString("urlPattern"),
                    cfg.getString("token")
            );
        }

        @Override
        public ExternalTokenAuth toGitAuth() {
            return ExternalTokenAuth.Oauth.builder()
                    .baseUrl(this.urlPattern())
                    .token(this.token())
                    .build();
        }
    }

    record AppInstallationConfig(String urlPattern, String apiUrl, String clientId, String privateKey) implements AuthConfig {

        static AppInstallationConfig from(com.typesafe.config.Config c) {
            return new AppInstallationConfig(
                    c.getString("urlPattern"),
                    c.getString("apiUrl"),
                    c.getString("clientId"),
                    c.getString("privateKey")
            );
        }

        @Override
        public ExternalTokenAuth toGitAuth() {
            try {
                var pkData = Files.readString(Paths.get(this.privateKey()));

                return AppInstallationAuth.builder()
                        .baseUrl(this.urlPattern())
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
