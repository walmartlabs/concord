package com.walmartlabs.concord.server.cfg;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.common.cfg.MappingAuthConfig;
import com.walmartlabs.concord.common.cfg.OauthTokenConfig;
import com.walmartlabs.concord.config.Config;
import org.eclipse.sisu.Nullable;

import javax.inject.Inject;
import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class GitConfiguration implements OauthTokenConfig, Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    @Config("git.oauth")
    @Nullable
    private String oauthToken;

    @Inject
    @Config("git.oauthUsername")
    @Nullable
    private String oauthUsername;

    @Inject
    @Config("git.oauthUrlPattern")
    @Nullable
    private String oauthUrlPattern;

    @Inject
    @Config("git.shallowClone")
    private boolean shallowClone;

    @Inject
    @Config("git.checkAlreadyFetched")
    private boolean checkAlreadyFetched;

    @Inject
    @Config("git.defaultOperationTimeout")
    private Duration defaultOperationTimeout;

    @Inject
    @Config("git.fetchTimeout")
    private Duration fetchTimeout;

    @Inject
    @Config("git.httpLowSpeedLimit")
    private int httpLowSpeedLimit;

    @Inject
    @Config("git.httpLowSpeedTime")
    private Duration httpLowSpeedTime;

    @Inject
    @Config("git.sshTimeout")
    private Duration sshTimeout;

    @Inject
    @Config("git.sshTimeoutRetryCount")
    private int sshTimeoutRetryCount;

    @Inject
    @Config("git.allowedSchemes")
    private List<String> allowedSchemes;

    @Inject
    @Config("git.systemAuth")
    List<com.typesafe.config.Config> authConfigs;

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

    @Override
    public Optional<String> getOauthToken() {
        return Optional.ofNullable(oauthToken);
    }

    @Override
    public Optional<String> getOauthUsername() {
        return Optional.ofNullable(oauthUsername);
    }

    @Override
    public Optional<String> getOauthUrlPattern() {
        return Optional.ofNullable(oauthUrlPattern);
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

    public List<String> getAllowedSchemes() { return allowedSchemes; }

    public List<MappingAuthConfig> getSystemAuth() {
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
        MappingAuthConfig toGitAuth();
    }

    public record OauthConfig(String urlPattern, String token) implements AuthConfig {

        static OauthConfig from(com.typesafe.config.Config cfg) {
            return new OauthConfig(
                    cfg.getString("urlPattern"),
                    cfg.getString("token")
            );
        }

        @Override
        public MappingAuthConfig.OauthAuthConfig toGitAuth() {
            return MappingAuthConfig.OauthAuthConfig.builder()
                    .urlPattern(MappingAuthConfig.assertBaseUrlPattern(this.urlPattern()))
                    .token(this.token())
                    .build();
        }
    }

}
