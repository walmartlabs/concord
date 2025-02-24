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

import javax.inject.Inject;
import java.time.Duration;

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
