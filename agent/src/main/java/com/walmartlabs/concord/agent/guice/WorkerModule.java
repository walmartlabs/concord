package com.walmartlabs.concord.agent.guice;

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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.agent.DefaultStateFetcher;
import com.walmartlabs.concord.agent.StateFetcher;
import com.walmartlabs.concord.agent.logging.*;
import com.walmartlabs.concord.agent.remote.ApiClientFactory;
import com.walmartlabs.concord.agent.remote.ProcessStatusUpdater;
import com.walmartlabs.concord.dependencymanager.DependencyManagerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

public class WorkerModule extends AbstractModule {

    private static final Logger log = LoggerFactory.getLogger(WorkerModule.class);

    private static final String REDIRECT_PROCESS_LOGS_TO_STDOUT_KEY = "REDIRECT_PROCESS_LOGS_TO_STDOUT";

    private final String agentId;
    private final UUID instanceId;
    private final String sessionToken;

    public WorkerModule(String agentId, UUID instanceId, String sessionToken) {
        this.agentId = agentId;
        this.instanceId = instanceId;
        this.sessionToken = sessionToken;
    }

    @Provides
    @Singleton
    ApiClient getApiClient(ApiClientFactory factory) throws IOException {
        return factory.create(sessionToken);
    }

    @Provides
    @Singleton
    ProcessLog getProcessLog(ApiClient apiClient) {
        return new RemoteProcessLog(instanceId, new RemoteLogAppender(apiClient));
    }

    @Provides
    @Singleton
    SecretClient getSecretClient(ApiClient apiClient) {
        return new SecretClient(apiClient);
    }

    @Provides
    @Singleton
    ProcessApi getProcessApi(ApiClient apiClient) {
        return new ProcessApi(apiClient);
    }

    @Provides
    @Singleton
    ProcessStatusUpdater getProcessStatusUpdater(ProcessApi processApi) {
        return new ProcessStatusUpdater(agentId, processApi);
    }

    @Override
    protected void configure() {
        bind(StateFetcher.class).to(DefaultStateFetcher.class);

        Multibinder<LogAppender> logAppenders = Multibinder.newSetBinder(binder(), LogAppender.class);
        logAppenders.addBinding().to(RemoteLogAppender.class);

        if (Boolean.parseBoolean(System.getenv(REDIRECT_PROCESS_LOGS_TO_STDOUT_KEY))) {
            log.info("Redirecting process logs into the agent's stdout...");
            logAppenders.addBinding().to(StdOutLogAppender.class);
        }

        bind(LogAppender.class).to(CombinedLogAppender.class);

        bind(AgentImportManager.class).toProvider(AgentImportManagerProvider.class);

        bind(DependencyManagerConfiguration.class).toProvider(AgentDependencyManagerConfigurationProvider.class);
    }
}
