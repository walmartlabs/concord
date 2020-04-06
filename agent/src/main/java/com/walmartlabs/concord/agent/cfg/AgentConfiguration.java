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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.agent.cfg.Utils.getDir;
import static com.walmartlabs.concord.agent.cfg.Utils.getStringOrDefault;

@Named
@Singleton
public class AgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentConfiguration.class);

    private final String agentId;
    private final Map<String, Object> capabilities;

    private final Path dependencyCacheDir;
    private final Path dependencyListsDir;
    private final Path payloadDir;

    private final Path logDir;
    private final long logMaxDelay;

    private final int workersCount;
    private final long pollInterval;
    private final int maintenanceModeListenerPort;

    @Inject
    public AgentConfiguration(Config cfg) {
        this.agentId = getStringOrDefault(cfg, "id", () -> UUID.randomUUID().toString());
        log.info("Using agent ID: {}", this.agentId);

        this.capabilities = cfg.hasPath("capabilities") ? cfg.getObject("capabilities").unwrapped() : null;
        log.info("Using the capabilities: {}", this.capabilities);

        this.dependencyCacheDir = getDir(cfg, "dependencyCacheDir");
        this.dependencyListsDir = getDir(cfg, "dependencyListsDir");
        this.payloadDir = getDir(cfg, "payloadDir");

        this.logDir = getDir(cfg, "logDir");
        this.logMaxDelay = cfg.getDuration("logMaxDelay", TimeUnit.MILLISECONDS);

        this.workersCount = cfg.getInt("workersCount");
        this.maintenanceModeListenerPort = cfg.getInt("maintenanceModeListenerPort");

        this.pollInterval = cfg.getDuration("pollInterval", TimeUnit.MILLISECONDS);
    }

    public String getAgentId() {
        return agentId;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    public Path getDependencyCacheDir() {
        return dependencyCacheDir;
    }

    public Path getDependencyListsDir() {
        return dependencyListsDir;
    }

    public Path getPayloadDir() {
        return payloadDir;
    }

    public Path getLogDir() {
        return logDir;
    }

    public long getLogMaxDelay() {
        return logMaxDelay;
    }

    public int getWorkersCount() {
        return workersCount;
    }

    public long getPollInterval() {
        return pollInterval;
    }

    public int getMaintenanceModeListenerPort() {
        return maintenanceModeListenerPort;
    }
}
