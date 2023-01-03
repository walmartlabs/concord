package com.walmartlabs.concord.server.cfg;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.ollie.config.Config;
import org.eclipse.sisu.Nullable;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;
import java.time.Duration;

@Named
@Singleton
public class GitConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    @Config("git.oauth")
    @Nullable
    private String oauthToken;

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
}
