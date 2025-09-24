package com.walmartlabs.concord.agent;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.typesafe.config.Config;
import com.walmartlabs.concord.agent.cfg.*;
import com.walmartlabs.concord.agent.executors.runner.DefaultDependencies;
import com.walmartlabs.concord.agent.executors.runner.ProcessPool;
import com.walmartlabs.concord.agent.remote.ApiClientFactory;
import com.walmartlabs.concord.agent.remote.QueueClientProvider;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import com.walmartlabs.concord.common.cfg.OauthTokenConfig;
import com.walmartlabs.concord.config.ConfigModule;
import com.walmartlabs.concord.github.appinstallation.cfg.GitHubAppInstallationConfig;
import com.walmartlabs.concord.server.queueclient.QueueClient;

import javax.inject.Named;

import static com.google.inject.Scopes.SINGLETON;

@Named
public class AgentModule implements Module {

    private final Config config;

    public AgentModule() {
        this(loadDefaultConfig());
    }

    public AgentModule(Config config) {
        this.config = config;
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(ObjectMapper.class).toProvider(ObjectMapperProvider.class);
        binder.bind(Config.class).toInstance(config);

        binder.bind(AgentConfiguration.class).in(SINGLETON);
        binder.bind(DockerConfiguration.class).in(SINGLETON);
        binder.bind(RuntimeConfiguration.class).asEagerSingleton();
        binder.bind(GitConfiguration.class).in(SINGLETON);
        binder.bind(OauthTokenConfig.class).to(GitConfiguration.class).in(SINGLETON);
        binder.bind(GitHubConfiguration.class).in(SINGLETON);
        binder.bind(GitHubAppInstallationConfig.class).to(GitHubConfiguration.class).in(SINGLETON);
        binder.bind(AgentAuthTokenProvider.ConcordServerTokenProvider.class).in(SINGLETON);
        binder.bind(ImportConfiguration.class).in(SINGLETON);
        binder.bind(PreForkConfiguration.class).in(SINGLETON);
        binder.bind(RepositoryCacheConfiguration.class).in(SINGLETON);
        binder.bind(ServerConfiguration.class).in(SINGLETON);

        binder.bind(DefaultDependencies.class).in(SINGLETON);
        binder.bind(ProcessPool.class).in(SINGLETON);
        binder.bind(ApiClientFactory.class).in(SINGLETON);
        binder.bind(QueueClient.class).toProvider(QueueClientProvider.class).in(SINGLETON);

        binder.bind(Agent.class).in(SINGLETON);
    }

    private static Config loadDefaultConfig() {
        return ConfigModule.load("concord-agent");
    }
}
