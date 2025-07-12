package com.walmartlabs.concord.server.cfg;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.typesafe.config.Config;
import com.walmartlabs.concord.config.ConfigModule;

import static com.google.inject.Scopes.SINGLETON;

public class ConfigurationModule implements Module {

    private final Config config;

    public ConfigurationModule(Config config) {
        this.config = config;
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(Config.class).toInstance(config);

        binder.install(new ConfigModule("com.walmartlabs.concord.server", config));

        binder.bind(AgentConfiguration.class).in(SINGLETON);
        binder.bind(ApiKeyConfiguration.class).in(SINGLETON);
        binder.bind(AuditConfiguration.class).in(SINGLETON);
        binder.bind(ConcordSecretStoreConfiguration.class).in(SINGLETON);
        binder.bind(ConsoleConfiguration.class).in(SINGLETON);
        binder.bind(CustomFormConfiguration.class).in(SINGLETON);
        binder.bind(DependenciesConfiguration.class).in(SINGLETON);
        binder.bind(EmailNotifierConfiguration.class).in(SINGLETON);
        binder.bind(EnqueueWorkersConfiguration.class).in(SINGLETON);
        binder.bind(ExternalEventsConfiguration.class).in(SINGLETON);
        binder.bind(GitConfiguration.class).in(SINGLETON);
        binder.bind(GithubConfiguration.class).in(SINGLETON);
        binder.bind(ImportConfiguration.class).in(SINGLETON);
        binder.bind(LdapConfiguration.class).in(SINGLETON);
        binder.bind(LdapGroupSyncConfiguration.class).in(SINGLETON);
        binder.bind(LockingConfiguration.class).in(SINGLETON);
        binder.bind(PolicyCacheConfiguration.class).in(SINGLETON);
        binder.bind(ProcessConfiguration.class).in(SINGLETON);
        binder.bind(ProcessQueueConfiguration.class).in(SINGLETON);
        binder.bind(ProcessWaitWatchdogConfiguration.class).in(SINGLETON);
        binder.bind(ProcessWatchdogConfiguration.class).in(SINGLETON);
        binder.bind(QosConfiguration.class).in(SINGLETON);
        binder.bind(RememberMeConfiguration.class).in(SINGLETON);
        binder.bind(RepositoryConfiguration.class).in(SINGLETON);
        binder.bind(SecretStoreConfiguration.class).in(SINGLETON);
        binder.bind(ServerConfiguration.class).in(SINGLETON);
        binder.bind(TemplatesConfiguration.class).in(SINGLETON);
        binder.bind(TriggersConfiguration.class).in(SINGLETON);
        binder.bind(WorkerMetricsConfiguration.class).in(SINGLETON);
    }
}
