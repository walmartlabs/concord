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

import com.walmartlabs.concord.common.cfg.ExternalTokenAuth;
import com.walmartlabs.concord.config.Config;
import com.walmartlabs.concord.github.appinstallation.cfg.GithubAppInstallationConfig;
import org.eclipse.sisu.Nullable;

import javax.inject.Inject;
import java.time.Duration;
import java.util.List;

public class GithubConfiguration implements GithubAppInstallationConfig {

    @Inject
    @Config("github.secret")
    @Nullable
    private String secret;

    @Inject
    @Config("github.useSenderLdapDn")
    private boolean useSenderLdapDn;

    @Inject
    @Config("github.logEvents")
    private boolean logEvents;

    @Inject
    @Config("github.disableReposOnDeletedRef")
    private boolean disableReposOnDeletedRef;

    private final GithubAppInstallationConfig appInstallation;

    @Inject
    public GithubConfiguration(com.typesafe.config.Config config) {
        if (config.hasPath("github.appInstallation")) {
            var raw = config.getConfig("github.appInstallation");
            this.appInstallation = GithubAppInstallationConfig.fromConfig(raw);
        } else {
            this.appInstallation = GithubAppInstallationConfig.builder().authConfigs(List.of()).build();
        }
    }

    public String getSecret() {
        return secret;
    }

    public boolean isUseSenderLdapDn() {
        return useSenderLdapDn;
    }

    public boolean isLogEvents() {
        return logEvents;
    }

    public boolean isDisableReposOnDeletedRef() {
        return disableReposOnDeletedRef;
    }

    @Override
    public List<ExternalTokenAuth> getAuthConfigs() {
        return appInstallation.getAuthConfigs();
    }

    @Override
    public Duration getHttpClientTimeout() {
        return appInstallation.getHttpClientTimeout();
    }

    @Override
    public Duration getSystemAuthCacheDuration() {
        return appInstallation.getSystemAuthCacheDuration();
    }

    @Override
    public long getSystemAuthCacheMaxWeight() {
        return appInstallation.getSystemAuthCacheMaxWeight();
    }

}
