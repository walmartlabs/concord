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
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.agent.cfg.Utils.getOrCreatePath;
import static com.walmartlabs.concord.agent.cfg.Utils.getStringOrDefault;

public class AgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentConfiguration.class);

    private final String agentId;
    private final Map<String, Object> capabilities;

    private final Path dependencyCacheDir;
    private final Path dependencyListsDir;
    private final Duration dependencyResolveTimeout;
    private final boolean dependencyStrictRepositories;
    private final List<String> dependencyExclusions;

    private final Path payloadDir;
    private final Path workDirBase;

    private final Path logDir;
    private final long logMaxDelay;

    private final int workersCount;
    private final long pollInterval;
    private final String maintenanceModeListenerHost;
    private final int maintenanceModeListenerPort;

    private final boolean explicitlyResolveV1Client;
    private final boolean mavenOfflineMode;

    @Inject
    public AgentConfiguration(Config cfg) {
        this.agentId = getStringOrDefault(cfg, "id", () -> UUID.randomUUID().toString());
        log.info("Using agent ID: {}", this.agentId);

        this.capabilities = cfg.hasPath("capabilities") ? cfg.getObject("capabilities").unwrapped() : null;
        log.info("Using the capabilities: {}", this.capabilities);

        this.dependencyCacheDir = getOrCreatePath(cfg, "dependencyCacheDir");
        this.dependencyListsDir = getOrCreatePath(cfg, "dependencyListsDir");
        this.dependencyResolveTimeout = cfg.hasPath("dependencyResolveTimeout") ? cfg.getDuration("dependencyResolveTimeout") : null;
        this.dependencyStrictRepositories = cfg.hasPath("dependencyStrictRepositories") && cfg.getBoolean("dependencyStrictRepositories");
        this.dependencyExclusions = cfg.getStringList("dependencyExclusions");

        this.payloadDir = getOrCreatePath(cfg, "payloadDir");
        this.workDirBase = getOrCreatePath(cfg, "workDirBase");

        this.logDir = getOrCreatePath(cfg, "logDir");
        this.logMaxDelay = cfg.getDuration("logMaxDelay", TimeUnit.MILLISECONDS);

        this.workersCount = cfg.getInt("workersCount");
        this.maintenanceModeListenerHost = cfg.getString("maintenanceModeListenerHost");
        this.maintenanceModeListenerPort = cfg.getInt("maintenanceModeListenerPort");

        this.pollInterval = cfg.getDuration("pollInterval", TimeUnit.MILLISECONDS);

        this.explicitlyResolveV1Client = cfg.getBoolean("explicitlyResolveV1Client");
        this.mavenOfflineMode = cfg.getBoolean("mavenOfflineMode");
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

    public Duration getDependencyResolveTimeout() {
        return dependencyResolveTimeout;
    }

    public boolean dependencyStrictRepositories() {
        return dependencyStrictRepositories;
    }

    public List<String> dependencyExclusions() {
        return dependencyExclusions;
    }

    public Path getPayloadDir() {
        return payloadDir;
    }

    public Path getWorkDirBase() {
        return workDirBase;
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

    public String getMaintenanceModeListenerHost() {
        return maintenanceModeListenerHost;
    }

    public int getMaintenanceModeListenerPort() {
        return maintenanceModeListenerPort;
    }

    public boolean isExplicitlyResolveV1Client() {
        return explicitlyResolveV1Client;
    }

    public boolean isMavenOfflineMode() {
        return mavenOfflineMode;
    }
}
