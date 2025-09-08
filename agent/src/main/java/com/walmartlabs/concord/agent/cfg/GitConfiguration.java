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
import com.walmartlabs.concord.repository.auth.AccessToken;
import com.walmartlabs.concord.repository.auth.AppInstallation;
import com.walmartlabs.concord.repository.auth.GitAuth;

import javax.inject.Inject;
import java.net.URI;
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
        this.authConfigs = cfg.getObjectList("git.authConfigs");
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

    public List<AuthConfig> getAuthConfigs() {

        return authConfigs.stream()
                .map(o -> {
                    GitAuthType type = GitAuthType.valueOf(o.toConfig().getString("type").toUpperCase());

                    return (AuthConfig) switch (type) {
                        case OAUTH -> OauthConfig.from(o.toConfig());
                        case APP_INSTALLATION -> AppInstallationConfig.from(o.toConfig());
                    };
                })
                .toList();
    }

    enum GitAuthType {
        OAUTH,
        APP_INSTALLATION
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
            return AccessToken.builder()
                    .baseUrl(URI.create(this.gitHost()))
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
            return AppInstallation.builder()
                    .baseUrl(URI.create(this.gitHost()))
                    .clientId(this.clientId())
                    .privateKey(Paths.get(this.privateKey()))
                    .build();
        }
    }
}
