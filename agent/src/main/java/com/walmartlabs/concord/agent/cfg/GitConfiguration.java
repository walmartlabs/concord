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
import com.walmartlabs.concord.common.cfg.ExternalTokenAuth;
import com.walmartlabs.concord.common.cfg.OauthTokenConfig;

import javax.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.walmartlabs.concord.agent.cfg.Utils.getStringOrDefault;

public class GitConfiguration implements OauthTokenConfig {

    private final String token;
    private final String oauthUsername;
    private final String oauthUrlPattern;
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
    private final List<? extends Config> authConfigs;

    @Inject
    public GitConfiguration(Config cfg) {
        this.token = getStringOrDefault(cfg, "git.oauth", () -> null);
        this.oauthUsername = getStringOrDefault(cfg, "git.oauthUsername", () -> null);
        this.oauthUrlPattern = getStringOrDefault(cfg, "git.oauthUrlPattern", () -> null);
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
        this.authConfigs = cfg.getConfigList("git.systemAuth");
    }

    @Override
    public Optional<String> getOauthToken() {
        return Optional.ofNullable(token);
    }

    @Override
    public Optional<String> getOauthUsername() {
        return Optional.ofNullable(oauthUsername);
    }

    @Override
    public Optional<String> getOauthUrlPattern() {
        return Optional.ofNullable(oauthUrlPattern);
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

    public List<ExternalTokenAuth> getSystemAuth() {
        return authConfigs.stream()
                .map(o -> {
                    AuthSource type = AuthSource.valueOf(o.getString("type").toUpperCase());

                    return (AuthConfig) switch (type) {
                        case OAUTH_TOKEN -> OauthConfig.from(o);
                    };
                })
                .map(AuthConfig::toGitAuth)
                .toList();

    }

    enum AuthSource {
        OAUTH_TOKEN
    }

    public interface AuthConfig {
        ExternalTokenAuth toGitAuth();
    }

    public record OauthConfig(String urlPattern, String token) implements AuthConfig {

        static OauthConfig from(Config cfg) {
            return new OauthConfig(
                    cfg.getString("urlPattern"),
                    cfg.getString("token")
            );
        }

        @Override
        public ExternalTokenAuth.Oauth toGitAuth() {
            return ExternalTokenAuth.Oauth.builder()
                    .urlPattern(this.urlPattern())
                    .token(this.token())
                    .build();
        }
    }

}
