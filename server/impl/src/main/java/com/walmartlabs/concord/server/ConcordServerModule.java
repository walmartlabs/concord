package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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
import com.walmartlabs.concord.common.ObjectMapperProvider;
import com.walmartlabs.concord.config.ConfigModule;
import com.walmartlabs.concord.db.DatabaseModule;
import com.walmartlabs.concord.dependencymanager.DependencyManagerConfiguration;
import com.walmartlabs.concord.server.agent.AgentModule;
import com.walmartlabs.concord.server.audit.AuditLogModule;
import com.walmartlabs.concord.server.boot.BackgroundTasks;
import com.walmartlabs.concord.server.cfg.ConfigurationModule;
import com.walmartlabs.concord.server.cfg.DatabaseConfigurationModule;
import com.walmartlabs.concord.server.console.ConsoleModule;
import com.walmartlabs.concord.server.events.EventModule;
import com.walmartlabs.concord.server.metrics.MetricModule;
import com.walmartlabs.concord.server.org.OrganizationModule;
import com.walmartlabs.concord.server.policy.PolicyModule;
import com.walmartlabs.concord.server.process.ProcessModule;
import com.walmartlabs.concord.server.repository.RepositoryModule;
import com.walmartlabs.concord.server.role.RoleModule;
import com.walmartlabs.concord.server.security.SecurityModule;
import com.walmartlabs.concord.server.security.apikey.ApiKeyModule;
import com.walmartlabs.concord.server.task.TaskSchedulerModule;
import com.walmartlabs.concord.server.template.TemplateModule;

import javax.inject.Named;

import static com.google.inject.Scopes.SINGLETON;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;

/**
 * Main module to run Concord Server with all features enabled.
 */
@Named
public class ConcordServerModule implements Module {

    private final Config config;

    public ConcordServerModule() {
        this(loadDefaultConfig());
    }

    public ConcordServerModule(Config config) {
        this.config = config;
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(ObjectMapper.class).toProvider(ObjectMapperProvider.class);

        binder.install(new ConfigurationModule(config));
        binder.install(new MetricModule());

        binder.install(new DatabaseConfigurationModule());
        binder.install(new DatabaseModule());

        binder.install(new TaskSchedulerModule());
        binder.bind(BackgroundTasks.class).in(SINGLETON);

        binder.bind(DependencyManagerConfiguration.class).toProvider(DependencyManagerConfigurationProvider.class);

        binder.install(new ApiServerModule());

        binder.install(new AgentModule());
        binder.install(new ApiKeyModule());
        binder.install(new AuditLogModule());
        binder.install(new ConsoleModule());
        binder.install(new EventModule());
        binder.install(new OrganizationModule());
        binder.install(new PolicyModule());
        binder.install(new ProcessModule());
        binder.install(new RepositoryModule());
        binder.install(new RoleModule());
        binder.install(new SecurityModule());
        binder.install(new TemplateModule());

        bindJaxRsResource(binder, ServerResource.class);
    }

    private static Config loadDefaultConfig() {
        return ConfigModule.load("concord-server");
    }
}
