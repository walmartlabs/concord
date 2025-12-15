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
import com.walmartlabs.concord.repository.AuthType;
import com.walmartlabs.concord.repository.GitAuthProvider;
import com.walmartlabs.concord.repository.ImmutableGitAuthProvider;

import javax.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.agent.cfg.Utils.getStringOrDefault;

public class GitConfiguration {

    private final List<GitAuthProvider> systemGitAuthProviders;
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

    @Inject
    public GitConfiguration(Config cfg) {
        this.token = getStringOrDefault(cfg, "git.oauth", () -> null);
        this.systemGitAuthProviders = cfg.hasPath("git.systemGitAuthProviders")
                ? cfg.getConfigList("git.systemGitAuthProviders").stream()
                .map(GitConfiguration::buildAuthProvider)
                .collect(Collectors.toList())
                : null;
        this.shallowClone = cfg.getBoolean("git.shallowClone");
        this.checkAlreadyFetched = cfg.getBoolean("git.checkAlreadyFetched");
        this.defaultOperationTimeout = cfg.getDuration("git.defaultOperationTimeout");
        this.fetchTimeout = cfg.getDuration("git.fetchTimeout");
        this.httpLowSpeedLimit = cfg.getInt("git.httpLowSpeedLimit");
        this.httpLowSpeedTime = cfg.getDuration("git.httpLowSpeedTime");
        this.sshTimeout = cfg.getDuration("git.sshTimeout");
        this.sshTimeoutRetryCount = cfg.getInt("git.sshTimeoutRetryCount");
        this.skip = cfg.getBoolean("git.skip");
    }

        private static GitAuthProvider buildAuthProvider(Config c) {
        ImmutableGitAuthProvider.Builder b = ImmutableGitAuthProvider.builder()
                .authType(AuthType.valueOf(c.getString("type")))
                .baseUrl(getOptString(c, "baseUrl"));

        // Optional fields depending on type
        if (c.hasPath("oauthToken")) {
            b.oauthToken(c.getString("oauthToken"));
        }
        if (c.hasPath("clientId")) {
            b.clientId(c.getString("clientId"));
        }
        if (c.hasPath("privateKey")) {
            b.privateKey(c.getString("privateKey"));
        }
        if (c.hasPath("installationId")) {
            b.installationId(c.getString("installationId"));
        }
        return b.build();
    }

    private static String getOptString(Config c, String k) {
        return c.hasPath(k) ? c.getString(k) : null;
    }

    private static boolean getBoolean(Config c, String k, boolean def) {
        return c.hasPath(k) ? c.getBoolean(k) : def;
    }

    private static int getInt(Config c, String k, int def) {
        return c.hasPath(k) ? c.getInt(k) : def;
    }

    private static Duration getDuration(Config c, String k, Duration def) {
        return c.hasPath(k) ? c.getDuration(k) : def;
    }

    public List<GitAuthProvider> getSystemGitAuthProviders() {
        return systemGitAuthProviders;
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
}
