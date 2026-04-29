package com.walmartlabs.concord.it.testingserver;

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

import com.google.inject.Guice;
import com.google.inject.Module;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;
import com.walmartlabs.concord.agent.Agent;
import com.walmartlabs.concord.agent.AgentModule;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A helper class for running concord-agent.
 * The agent runs in the same JVM as TestingConcordAgent.
 */
public class TestingConcordAgent implements AutoCloseable {

    private final Map<String, String> extraConfiguration;
    private final List<Function<Config, Module>> extraModules;
    private final int apiPort;
    private final String agentApiKey;

    private Agent agent;

    public TestingConcordAgent(TestingConcordServer testingConcordServer) {
        this(testingConcordServer, Map.of(), List.of());
    }

    public TestingConcordAgent(TestingConcordServer testingConcordServer, Map<String, String> extraConfiguration, List<Function<Config, Module>> extraModules) {
        this.apiPort = testingConcordServer.getApiPort();
        this.agentApiKey = testingConcordServer.getAgentApiKey();
        this.extraConfiguration = extraConfiguration;
        this.extraModules = extraModules;
    }

    public synchronized void start() {
        var config = prepareConfig();
        var system = new AgentModule(config);
        var allModules = Stream.concat(extraModules.stream().map(f -> f.apply(config)), Stream.of(system)).toList();
        var injector = Guice.createInjector(allModules);
        agent = injector.getInstance(Agent.class);
        agent.start();
    }

    public synchronized void stop() {
        if (agent != null) {
            agent.stop();
            agent = null;
        }
    }

    @Override
    public void close() {
        this.stop();
    }

    private Config prepareConfig() {
        var extraConfig = ConfigFactory.parseMap(this.extraConfiguration);

        var testConfig = ConfigFactory.parseMap(Map.of(
                "maintenanceModeListenerPort", 0,
                "workDirBase", "workDirs",
                "server.apiBaseUrl", "http://localhost:" + apiPort,
                "server.websocketUrl", "ws://localhost:" + apiPort + "/websocket",
                "server.apiKey", agentApiKey
        ));

        var defaultConfig = ConfigFactory.load("concord-agent.conf", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults().setAllowUnresolved(true))
                .getConfig("concord-agent");

        return extraConfig.withFallback(testConfig.withFallback(defaultConfig)).resolve();
    }
}
