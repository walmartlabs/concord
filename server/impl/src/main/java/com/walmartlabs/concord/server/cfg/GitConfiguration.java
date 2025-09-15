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

import com.walmartlabs.concord.common.GitAuth;
import com.walmartlabs.concord.config.Config;
import com.walmartlabs.concord.github.appinstallation.AppInstallation;
import org.eclipse.sisu.Nullable;

import javax.inject.Inject;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

public class GitConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    @Config("git.oauth")
    @Nullable
    private String oauthToken;

    @Inject
    @Config("git.systemAuth")
    private List<com.typesafe.config.Config> authConfigs;

    @Inject
    @Config("git.systemAuthCacheDuration")
    private Duration systemAuthCacheDuration;

    @Inject
    @Config("git.systemAuthCacheMaxSize")
    private int systemAuthCacheMaxSize;

    @Inject
    @Config("git.authorizedGitHosts")
    @Nullable
    private List<String> authorizedGitHosts;

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

    public String getOauthToken() {
        return oauthToken;
    }

    public List<GitAuth> getAuthConfigs() {
        // new, tighter, list of configs mapped to specific git hosts
        var cfgs = new LinkedList<>(toGitAuth());

        // Backwards compat for a single, global oauth token. We need to remove
        // this eventually to keep things tidy, but migrating config and removing
        // git.oauth is sufficiently secure
        if (oauthToken != null) {
            cfgs.add(GitAuth.Oauth.builder()
                    .baseUrl(".*")
                    .token(oauthToken)
                    .build());
        }

        return cfgs;
    }

    private List<GitAuth> toGitAuth() {
        return authConfigs.stream()
                .map(o -> (AuthConfig) switch (GitAuthType.valueOf(o.getString("type").toUpperCase())) {
                    case OAUTH -> OauthConfig.from(o);
                    case GITHUB_APP_INSTALLATION -> AppInstallationConfig.from(o);
                })
                .map(AuthConfig::toGitAuth)
                .toList();
    }

    public Duration getSystemAuthCacheDuration() {
        return systemAuthCacheDuration;
    }

    public int getSystemAuthCacheMaxSize() {
        return systemAuthCacheMaxSize;
    }

    public List<String> getAuthorizedGitHosts() {
        return authorizedGitHosts;
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

    enum GitAuthType {
        OAUTH,
        GITHUB_APP_INSTALLATION
    }

    public interface AuthConfig {
        String gitHost();

        GitAuth toGitAuth();
    }

    public record OauthConfig(String gitHost, String token) implements AuthConfig {

        static OauthConfig from(com.typesafe.config.Config cfg) {
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

    public record AppInstallationConfig(String gitHost, String apiUrl, String clientId, String privateKey) implements AuthConfig {

        static AppInstallationConfig from(com.typesafe.config.Config c) {
            return new AppInstallationConfig(
                    c.getString("gitHost"),
                    c.getString("apiUrl"),
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
                        .apiUrl(this.apiUrl())
                        .build();
            } catch (IOException e) {
                throw new RuntimeException("Error initializing Git App Installation auth", e);
            }
        }
    }

}
