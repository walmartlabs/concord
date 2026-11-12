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
import com.google.inject.Guice;
import com.walmartlabs.concord.agent.RepositoryManager;
import com.walmartlabs.concord.agent.cfg.AgentConfiguration;
import com.walmartlabs.concord.agent.cfg.ImportConfiguration;
import com.walmartlabs.concord.agent.cfg.ServerConfiguration;
import com.walmartlabs.concord.agent.remote.ApiClientFactory;
import com.walmartlabs.concord.agent.remote.FinalizationHeartbeat;
import com.walmartlabs.concord.client2.DefaultApiClientFactory;
import com.walmartlabs.concord.common.ZipService;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkerModuleTest {

    @Test
    void eachWorkerOwnsItsHeartbeat() throws Exception {
        var agentCfg = mock(AgentConfiguration.class);
        when(agentCfg.getLogDrainTimeout()).thenReturn(Duration.ofMinutes(1));
        var apiFactory = mock(ApiClientFactory.class);
        when(apiFactory.create(anyString())).thenAnswer(ignored -> new DefaultApiClientFactory("http://127.0.0.1:1").create());
        var parent = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(AgentConfiguration.class).toInstance(agentCfg);
                bind(ServerConfiguration.class).toInstance(mock(ServerConfiguration.class));
                bind(ImportConfiguration.class).toInstance(mock(ImportConfiguration.class));
                bind(ApiClientFactory.class).toInstance(apiFactory);
                bind(RepositoryManager.class).toInstance(mock(RepositoryManager.class));
                bind(DependencyManager.class).toInstance(mock(DependencyManager.class));
                bind(ZipService.class).toInstance(mock(ZipService.class));
            }
        });
        var firstId = UUID.randomUUID();
        var first = parent.createChildInjector(new WorkerModule("agent", firstId, "first-token"));
        var second = parent.createChildInjector(new WorkerModule("agent", UUID.randomUUID(), "second-token"));
        try (var firstHeartbeat = first.getInstance(FinalizationHeartbeat.class);
             var secondHeartbeat = second.getInstance(FinalizationHeartbeat.class)) {
            assertSame(firstHeartbeat, first.getInstance(FinalizationHeartbeat.class));
            assertNotSame(firstHeartbeat, secondHeartbeat);
        }
    }
}
