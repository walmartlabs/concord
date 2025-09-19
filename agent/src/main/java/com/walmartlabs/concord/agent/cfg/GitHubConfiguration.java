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

import com.walmartlabs.concord.common.cfg.ExternalTokenAuth;
import com.walmartlabs.concord.github.appinstallation.cfg.GitHubAppInstallationConfig;

import javax.inject.Inject;
import java.time.Duration;
import java.util.List;

public class GitHubConfiguration implements GitHubAppInstallationConfig {

    private static final String CFG_APP_INSTALLATION = "github.appInstallation";

    private final GitHubAppInstallationConfig appInstallation;

    @Inject
    public GitHubConfiguration(com.typesafe.config.Config config) {
        if (config.hasPath(CFG_APP_INSTALLATION)) {
            var raw = config.getConfig(CFG_APP_INSTALLATION);
            this.appInstallation = GitHubAppInstallationConfig.fromConfig(raw);
        } else {
            this.appInstallation = GitHubAppInstallationConfig.builder()
                    .authConfigs(List.of())
                    .build();
        }
    }

    @Override
    public List<ExternalTokenAuth> getAuthConfigs() {
        return appInstallation.getAuthConfigs();
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
