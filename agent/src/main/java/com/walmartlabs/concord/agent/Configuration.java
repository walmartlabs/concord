package com.walmartlabs.concord.agent;

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

import com.typesafe.config.Config;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.ollie.config.ConfigurationProcessor;
import com.walmartlabs.ollie.config.EnvironmentSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class Configuration {

    private static final Logger log = LoggerFactory.getLogger(Configuration.class);

    private final String agentId;
    private final Map<String, Object> capabilities;
    private final Path dependencyCacheDir;
    private final Path dependencyListsDir;
    private final Path payloadDir;

    private final Path logDir;
    private final long logMaxDelay;

    private final int workersCount;
    private final Path javaPath;
    private final long pollInterval;

    private final long maxPreforkAge;
    private final int maxPreforkCount;

    private final String serverApiBaseUrl;
    private final String[] serverWebsocketUrls;
    private final boolean apiVerifySsl;
    private final long connectTimeout;
    private final long readTimeout;
    private final int retryCount;
    private final long retryInterval;
    private final String userAgent;
    private final long maxWebSocketInactivity;

    private final String dockerHost;
    private final boolean dockerOrphanSweeperEnabled;
    private final long dockerOrphanSweeperPeriod;
    private final List<String> extraDockerVolumes;

    private final Path repositoryCacheDir;
    private final long repositoryLockTimeout;

    private final String repositoryOauthToken;
    private final boolean shallowClone;
    private final int repositoryHttpLowSpeedLimit;
    private final int repositoryHttpLowSpeedTime;
    private final int repositorySshTimeout;
    private final int repositorySshTimeoutRetryCount;


    private final Path runnerPath;
    private final Path runnerCfgDir;
    private final String agentJavaCmd;

    private final boolean runnerSecurityManagerEnabled;

    private final String apiKey;

    public Configuration() {
        Config cfg = load("concord-agent");

        this.agentId = getStringOrDefault(cfg, "id", () -> UUID.randomUUID().toString());

        this.capabilities = cfg.hasPath("capabilities") ? cfg.getObject("capabilities").unwrapped() : null;
        log.info("Using the capabilities: {}", this.capabilities);

        this.dependencyCacheDir = getDir(cfg, "dependencyCacheDir");
        this.dependencyListsDir = getDir(cfg, "dependencyListsDir");
        this.payloadDir = getDir(cfg, "payloadDir");

        this.logDir = getDir(cfg, "logDir");
        this.logMaxDelay = cfg.getDuration("logMaxDelay", TimeUnit.MILLISECONDS);

        this.workersCount = cfg.getInt("workersCount");

        String java = getStringOrDefault(cfg, "javaPath", () -> System.getProperty("java.home"));
        if (java != null) {
            log.info("Using JAVA_PATH: {}", java);
            this.javaPath = Paths.get(java);
        } else {
            log.warn("Can't determine 'javaPath', running processes in containers may not work");
            this.javaPath = null;
        }

        this.pollInterval = cfg.getDuration("pollInterval", TimeUnit.MILLISECONDS);

        this.maxPreforkAge = cfg.getDuration("prefork.maxAge", TimeUnit.MILLISECONDS);
        this.maxPreforkCount = cfg.getInt("prefork.maxCount");

        this.serverApiBaseUrl = cfg.getString("server.apiBaseUrl");
        log.info("Using the Server's API address: {}", serverApiBaseUrl);
        this.serverWebsocketUrls = getCSV(cfg.getString("server.websockerUrl"));
        log.info("Using the Server's websocket addresses: {}", (Object[]) serverWebsocketUrls);

        this.apiVerifySsl = cfg.getBoolean("server.verifySsl");
        this.connectTimeout = cfg.getDuration("server.connectTimeout", TimeUnit.MILLISECONDS);
        this.readTimeout = cfg.getDuration("server.readTimeout", TimeUnit.MILLISECONDS);
        this.retryCount = cfg.getInt("server.retryCount");
        this.retryInterval = cfg.getDuration("server.retryInterval", TimeUnit.MILLISECONDS);
        this.userAgent = getStringOrDefault(cfg, "server.userAgent", () -> "Concord-Agent: id=" + this.agentId);
        this.maxWebSocketInactivity = cfg.getDuration("server.maxWebSocketInactivity", TimeUnit.MILLISECONDS);
        this.apiKey = cfg.getString("server.apiKey");

        this.dockerHost = cfg.getString("docker.host");
        this.dockerOrphanSweeperEnabled = cfg.getBoolean("docker.orphanSweeperEnabled");
        this.dockerOrphanSweeperPeriod = cfg.getDuration("docker.orphanSweeperPeriod", TimeUnit.MILLISECONDS);
        this.extraDockerVolumes = cfg.getStringList("docker.extraVolumes");

        this.repositoryCacheDir = getDir(cfg, "repositoryCache.cacheDir");
        this.repositoryLockTimeout = cfg.getDuration("repositoryCache.lockTimeout", TimeUnit.MILLISECONDS);

        this.repositoryOauthToken = getStringOrDefault(cfg, "git.oauth", () -> null);
        this.shallowClone = cfg.getBoolean("git.shallowClone");
        this.repositoryHttpLowSpeedLimit = cfg.getInt("git.httpLowSpeedLimit");
        this.repositoryHttpLowSpeedTime = cfg.getInt("git.httpLowSpeedTime");
        this.repositorySshTimeout = cfg.getInt("git.sshTimeout");
        this.repositorySshTimeoutRetryCount = cfg.getInt("git.sshTimeoutRetryCount");

        String path = getStringOrDefault(cfg, "runner.path", () -> {
            try {
                Properties props = new Properties();
                props.load(Configuration.class.getResourceAsStream("runner.properties"));
                return props.getProperty("runner.path");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        this.runnerPath = Paths.get(path);
        this.runnerSecurityManagerEnabled = cfg.getBoolean("runner.securityManagerEnabled");
        this.runnerCfgDir = getDir(cfg, "runner.cfgDir");
        this.agentJavaCmd = cfg.getString("runner.javaCmd");
    }

    public String getAgentId() {
        return agentId;
    }

    public String getServerApiBaseUrl() {
        return serverApiBaseUrl;
    }

    public String[] getServerWebsocketUrls() {
        return serverWebsocketUrls;
    }

    public Path getLogDir() {
        return logDir;
    }

    public long getLogMaxDelay() {
        return logMaxDelay;
    }

    public Path getPayloadDir() {
        return payloadDir;
    }

    public String getAgentJavaCmd() {
        return agentJavaCmd;
    }

    public Path getDependencyCacheDir() {
        return dependencyCacheDir;
    }

    public Path getRunnerPath() {
        return runnerPath;
    }

    public Path getRunnerCfgDir() {
        return runnerCfgDir;
    }

    public int getWorkersCount() {
        return workersCount;
    }

    public long getMaxPreforkAge() {
        return maxPreforkAge;
    }

    public int getMaxPreforkCount() {
        return maxPreforkCount;
    }

    public String getDockerHost() {
        return dockerHost;
    }

    public boolean isDockerOrphanSweeperEnabled() {
        return dockerOrphanSweeperEnabled;
    }

    public long getDockerOrphanSweeperPeriod() {
        return dockerOrphanSweeperPeriod;
    }

    public List<String> getExtraDockerVolumes() {
        return extraDockerVolumes;
    }

    public boolean isRunnerSecurityManagerEnabled() {
        return runnerSecurityManagerEnabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public boolean isApiVerifySsl() {
        return apiVerifySsl;
    }

    public long getReadTimeout() {
        return readTimeout;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getRetryInterval() {
        return retryInterval;
    }

    public long getPollInterval() {
        return pollInterval;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Path getDependencyListsDir() {
        return dependencyListsDir;
    }

    public Path getJavaPath() {
        return javaPath;
    }

    public long getMaxWebSocketInactivity() {
        return maxWebSocketInactivity;
    }

    public String getRepositoryOauthToken() {
        return repositoryOauthToken;
    }

    public long getRepositoryLockTimeout() {
        return repositoryLockTimeout;
    }

    public Path getRepositoryCacheDir() {
        return repositoryCacheDir;
    }

    public int getRepositorySshTimeout() {
        return repositorySshTimeout;
    }

    public int getRepositorySshTimeoutRetryCount() {
        return repositorySshTimeoutRetryCount;
    }

    public int getRepositoryHttpLowSpeedLimit() {
        return repositoryHttpLowSpeedLimit;
    }

    public int getRepositoryHttpLowSpeedTime() {
        return repositoryHttpLowSpeedTime;
    }

    public boolean isShallowClone() {
        return shallowClone;
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "agentId='" + agentId + '\'' +
                ", capabilities=" + capabilities +
                ", dependencyCacheDir=" + dependencyCacheDir +
                ", dependencyListsDir=" + dependencyListsDir +
                ", payloadDir=" + payloadDir +
                ", logDir=" + logDir +
                ", logMaxDelay=" + logMaxDelay +
                ", workersCount=" + workersCount +
                ", javaPath=" + javaPath +
                ", pollInterval=" + pollInterval +
                ", maxPreforkAge=" + maxPreforkAge +
                ", maxPreforkCount=" + maxPreforkCount +
                ", serverApiBaseUrl='" + serverApiBaseUrl + '\'' +
                ", serverWebsocketUrls=" + serverWebsocketUrls +
                ", apiVerifySsl=" + apiVerifySsl +
                ", connectTimeout=" + connectTimeout +
                ", readTimeout=" + readTimeout +
                ", retryCount=" + retryCount +
                ", retryInterval=" + retryInterval +
                ", userAgent='" + userAgent + '\'' +
                ", maxWebSocketInactivity=" + maxWebSocketInactivity +
                ", dockerHost='" + dockerHost + '\'' +
                ", dockerOrphanSweeperEnabled=" + dockerOrphanSweeperEnabled +
                ", dockerOrphanSweeperPeriod=" + dockerOrphanSweeperPeriod +
                ", repositoryCacheDir=" + repositoryCacheDir +
                ", repositoryLockTimeout=" + repositoryLockTimeout +
                ", repositoryOauthToken='" + repositoryOauthToken + '\'' +
                ", shallowClone=" + shallowClone +
                ", repositoryHttpLowSpeedLimit=" + repositoryHttpLowSpeedLimit +
                ", repositoryHttpLowSpeedTime=" + repositoryHttpLowSpeedTime +
                ", repositorySshTimeout=" + repositorySshTimeout +
                ", repositorySshTimeoutRetryCount=" + repositorySshTimeoutRetryCount +
                ", runnerPath=" + runnerPath +
                ", agentJavaCmd='" + agentJavaCmd + '\'' +
                ", runnerSecurityManagerEnabled=" + runnerSecurityManagerEnabled +
                ", apiKey='" + apiKey + '\'' +
                '}';
    }

    private static Config load(String name) {
        EnvironmentSelector environmentSelector = new EnvironmentSelector();
        return new ConfigurationProcessor(name, environmentSelector.select()).process();
    }

    private static String getStringOrDefault(Config cfg, String key, Supplier<String> defaultValueSupplier) {
        if (cfg.hasPath(key)) {
            return cfg.getString(key);
        }
        return defaultValueSupplier.get();
    }

    private static Path getDir(Config cfg, String key) {
        try {
            if (!cfg.hasPath(key)) {
                return IOUtils.createTempDir(key);
            }

            String value = cfg.getString(key);
            if (value.startsWith("/")) {
                Path p = Paths.get(value);
                if (!Files.exists(p)) {
                    Files.createDirectories(p);
                }
                return p;
            }
            return IOUtils.createTempDir(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String[] getCSV(String s) {
        if (s == null) {
            return null;
        }

        String[] as = s.split(",");
        for (int i = 0; i < as.length; i++) {
            as[i] = as[i].trim();
        }

        return as;
    }
}
