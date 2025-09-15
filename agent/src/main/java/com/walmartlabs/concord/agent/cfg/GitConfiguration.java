package com.walmartlabs.concord.agent.cfg;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import com.typesafe.config.ConfigObject;
import com.walmartlabs.concord.common.cfg.GitAuth;
import com.walmartlabs.concord.github.appinstallation.cfg.AppInstallation;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import static com.walmartlabs.concord.agent.cfg.Utils.getStringOrDefault;

public class GitConfiguration {

    private final String token;
    private final boolean shallowClone;
    private final boolean checkAlreadyFetched;
    private final Duration defaultOperationTimeout;
    private final Duration fetchTimeout;
    private final int httpLowSpeedLimit;
    private final Duration httpLowSpeedTime;
    private final Duration sshTimeout;
    private final int sshTimeoutRetryCount;
    private final boolean skip;
    private final List<String> allowedSchemes;
    private final List<? extends ConfigObject> authConfigs;


    @Inject
    public GitConfiguration(Config cfg) {
        this.token = getStringOrDefault(cfg, "git.oauth", () -> null);
        this.shallowClone = cfg.getBoolean("git.shallowClone");
        this.checkAlreadyFetched = cfg.getBoolean("git.checkAlreadyFetched");
        this.defaultOperationTimeout = cfg.getDuration("git.defaultOperationTimeout");
        this.fetchTimeout = cfg.getDuration("git.fetchTimeout");
        this.httpLowSpeedLimit = cfg.getInt("git.httpLowSpeedLimit");
        this.httpLowSpeedTime = cfg.getDuration("git.httpLowSpeedTime");
        this.sshTimeout = cfg.getDuration("git.sshTimeout");
        this.sshTimeoutRetryCount = cfg.getInt("git.sshTimeoutRetryCount");
        this.skip = cfg.getBoolean("git.skip");
        this.allowedSchemes = cfg.getStringList("git.allowedSchemes");
        this.authConfigs = cfg.getObjectList("git.systemAuth"); //TODO rename variables to systemAuth, make sure it aligns with server
    }

    public String getToken() {
        return token;
    }

    public boolean isShallowClone() {
        return shallowClone;
    }

    public boolean isCheckAlreadyFetched() {
        return checkAlreadyFetched;
    }

    public Duration getDefaultOperationTimeout() {
        return defaultOperationTimeout;
    }

    public Duration getFetchTimeout() {
        return fetchTimeout;
    }

    public int getHttpLowSpeedLimit() {
        return httpLowSpeedLimit;
    }

    public Duration getHttpLowSpeedTime() {
        return httpLowSpeedTime;
    }

    public Duration getSshTimeout() {
        return sshTimeout;
    }

    public int getSshTimeoutRetryCount() {
        return sshTimeoutRetryCount;
    }

    public boolean isSkip() {
        return skip;
    }

    public List<String> getAllowedSchemes() { return allowedSchemes; }

    public List<GitAuth> getAuthConfigs() {

        return authConfigs.stream()
                .map(o -> {
                    GitAuthType type = GitAuthType.valueOf(o.toConfig().getString("type").toUpperCase());

                    return (AuthConfig) switch (type) {
                        case OAUTH -> OauthConfig.from(o.toConfig());
                        case GITHUB_APP_INSTALLATION -> AppInstallationConfig.from(o.toConfig());
                        case CONCORD_SERVER -> ConcordServerConfig.from(o.toConfig());
                    };
                })
                .map(AuthConfig::toGitAuth)
                .toList();
    }

    enum GitAuthType {
        OAUTH,
        GITHUB_APP_INSTALLATION,
        CONCORD_SERVER
    }

    public interface AuthConfig {
        String gitHost();

        GitAuth toGitAuth();
    }

    public record OauthConfig(String gitHost, String token) implements AuthConfig {

        static OauthConfig from(Config cfg) {
            return new OauthConfig(
                    cfg.getString("gitHost"),
                    cfg.getString("token")
            );
        }

        @Override
        public GitAuth toGitAuth() {
            return GitAuth.Oauth.builder()
                    .baseUrl(this.gitHost())
                    .token(this.token())
                    .build();
        }
    }

    public record AppInstallationConfig(String gitHost, String clientId, String privateKey) implements AuthConfig {

        static AppInstallationConfig from(Config c) {
            return new AppInstallationConfig(
                    c.getString("gitHost"),
                    c.getString("clientId"),
                    c.getString("privateKey")
            );
        }

        @Override
        public GitAuth toGitAuth() {
            try {
                var pkData = Files.readString(Paths.get(this.privateKey()));

                return AppInstallation.builder()
                        .baseUrl(this.gitHost())
                        .clientId(this.clientId())
                        .privateKey(pkData)
                        .apiUrl(null) // TODO fix
                        .build();
            } catch (IOException e) {
                throw new RuntimeException("Error initializing Git App Installation auth", e);
            }
        }
    }

    public record ConcordServerConfig(String gitHost) implements AuthConfig {

        static ConcordServerConfig from(Config cfg) {
            return new ConcordServerConfig(
                    cfg.getString("gitHost")
            );
        }

        @Override
        public GitAuth toGitAuth() {
            return GitAuth.ConcordServer.builder()
                    .baseUrl(this.gitHost())
                    .build();
        }
    }
}
