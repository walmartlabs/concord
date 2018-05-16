package com.walmartlabs.concord.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.walmartlabs.concord.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class Configuration {

    private static final Logger log = LoggerFactory.getLogger(Configuration.class);

    public static final String SERVER_HOST_KEY = "SERVER_HOST";
    public static final String SERVER_RPC_PORT_KEY = "SERVER_RPC_PORT";
    public static final String SERVER_API_BASE_URL_KEY = "SERVER_API_BASE_URL";
    public static final String LOG_DIR_KEY = "AGENT_LOG_DIR";
    public static final String LOG_MAX_DELAY_KEY = "LOG_MAX_DELAY";
    public static final String PAYLOAD_DIR_KEY = "AGENT_PAYLOAD_DIR";
    public static final String JAVA_CMD_KEY = "AGENT_JAVA_CMD";
    public static final String DEPENDENCY_CACHE_DIR_KEY = "DEPS_CACHE_DIR";
    public static final String RUNNER_PATH = "RUNNER_PATH";
    public static final String WORKERS_COUNT_KEY = "WORKERS_COUNT";
    public static final String MAX_PREFORK_AGE_KEY = "MAX_PREFORK_AGE";

    public static final String DOCKER_ORPHAN_SWEEPER_ENABLED_KEY = "DOCKER_ORPHAN_SWEEPER_ENABLED";
    public static final String DOCKER_ORPHAN_SWEEPER_PERIOD_KEY = "DOCKER_ORPHAN_SWEEPER_PERIOD";
    public static final String DOCKER_OLD_IMAGE_SWEEPER_ENABLED_KEY = "DOCKER_OLD_IMAGE_SWEEPER_ENABLED";
    public static final String DOCKER_OLD_IMAGE_SWEEPER_PERIOD_KEY = "DOCKER_OLD_IMAGE_SWEEPER_PERIOD";

    public static final String RUNNER_SECURITY_MANAGER_ENABLED_KEY = "RUNNER_SECURITY_MANAGER_ENABLED";

    public static final String API_KEY = "API_KEY";
    public static final String CONNECT_TIMEOUT_KEY = "API_CONNECT_TIMEOUT_KEY";
    public static final String READ_TIMEOUT_KEY = "API_READ_TIMEOUT_KEY";
    public static final String RETRY_COUNT_KEY = "API_RETRY_COUNT_KEY";
    public static final String RETRY_INTERVAL_KEY = "API_RETRY_INTERVAL_KEY";
    public static final String POLL_INTERVAL_KEY = "QUEUE_POLL_INTERVAL_KEY";
    public static final String MAINTENANCE_MODE_KEY = "MAINTENANCE_MODE_FILE";

    public static final String CAPABILITIES_FILE_KEY = "CAPABILITIES_FILE";

    /**
     * As defined in server/db/src/main/resources/com/walmartlabs/concord/server/db/v0.69.0.xml
     */
    private static final String DEFAULT_AGENT_API_KEY = "O+JMYwBsU797EKtlRQYu+Q";

    private final String agentId;
    private final String serverHost;
    private final int serverRpcPort;
    private final String serverApiBaseUrl;
    private final Path logDir;
    private final long logMaxDelay;
    private final Path payloadDir;
    private final String agentJavaCmd;
    private final Path dependencyCacheDir;
    private final Path runnerPath;
    private final int workersCount;
    private final long maxPreforkAge;
    private final boolean dockerOrphanSweeperEnabled;
    private final long dockerOrphanSweeperPeriod;
    private final boolean dockerOldImageSweeperEnabled;
    private final long dockerOldImageSweeperPeriod;
    private final boolean runnerSecurityManagerEnabled;

    private final String apiKey;
    private final int readTimeout;
    private final int connectTimeout;
    private final int retryCount;
    private final long retryInterval;
    private final long pollInterval;
    private final Path maintenanceModeFile;

    private final Map<String, Object> capabilities;

    @SuppressWarnings("unchecked")
    public Configuration() {
        this.agentId = UUID.randomUUID().toString();

        try {
            this.serverHost = getEnv(SERVER_HOST_KEY, "localhost");
            this.serverRpcPort = Integer.parseInt(getEnv(SERVER_RPC_PORT_KEY, "8101"));
            log.info("Using the RPC address {}:{}...", serverHost, serverRpcPort);

            this.serverApiBaseUrl = getEnv(SERVER_API_BASE_URL_KEY, "http://" + serverHost + ":8001");
            log.info("Using the API address: {}", serverApiBaseUrl);

            this.logDir = getDir(LOG_DIR_KEY, "logDir");
            this.logMaxDelay = Long.parseLong(getEnv(LOG_MAX_DELAY_KEY, "250")); // 250ms

            this.payloadDir = getDir(PAYLOAD_DIR_KEY, "payloadDir");
            this.agentJavaCmd = getEnv(JAVA_CMD_KEY, "java");
            this.dependencyCacheDir = getDir(DEPENDENCY_CACHE_DIR_KEY, "depsCacheDir");

            String s = System.getenv(RUNNER_PATH);
            if (s == null) {
                Properties props = new Properties();
                props.load(Configuration.class.getResourceAsStream("runner.properties"));
                s = props.getProperty("runner.path");
            }

            this.runnerPath = Paths.get(s);

            this.workersCount = Integer.parseInt(getEnv(WORKERS_COUNT_KEY, "3"));

            this.maxPreforkAge = Long.parseLong(getEnv(MAX_PREFORK_AGE_KEY, "30000"));

            this.dockerOrphanSweeperEnabled = Boolean.parseBoolean(getEnv(DOCKER_ORPHAN_SWEEPER_ENABLED_KEY, "false"));
            this.dockerOrphanSweeperPeriod = Long.parseLong(getEnv(DOCKER_ORPHAN_SWEEPER_PERIOD_KEY, "900000")); // 15 min

            this.dockerOldImageSweeperEnabled = Boolean.parseBoolean(getEnv(DOCKER_OLD_IMAGE_SWEEPER_ENABLED_KEY, "false"));
            this.dockerOldImageSweeperPeriod = Long.parseLong(getEnv(DOCKER_OLD_IMAGE_SWEEPER_PERIOD_KEY, "3600000")); // 1 hour

            this.runnerSecurityManagerEnabled = Boolean.parseBoolean(getEnv(RUNNER_SECURITY_MANAGER_ENABLED_KEY, "false"));

            this.apiKey = getEnv(API_KEY, DEFAULT_AGENT_API_KEY);
            this.connectTimeout = Integer.parseInt(getEnv(CONNECT_TIMEOUT_KEY, "10000"));
            this.readTimeout = Integer.parseInt(getEnv(READ_TIMEOUT_KEY, "10000"));
            this.retryCount = Integer.parseInt(getEnv(RETRY_COUNT_KEY, "5"));
            this.retryInterval = Integer.parseInt(getEnv(RETRY_INTERVAL_KEY, "30000"));
            this.pollInterval = Long.parseLong(getEnv(POLL_INTERVAL_KEY, "1000"));

            this.maintenanceModeFile = getDir(MAINTENANCE_MODE_KEY, "maintenance-mode").resolve("info");

            String capabilitiesFile = getEnv(CAPABILITIES_FILE_KEY, null);
            if (capabilitiesFile != null) {
                this.capabilities = new ObjectMapper().readValue(new File(capabilitiesFile), Map.class);
                log.info("Using the capabilities: {}", this.capabilities);
            } else {
                this.capabilities = null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getAgentId() {
        return agentId;
    }

    public String getServerHost() {
        return serverHost;
    }

    public int getServerRpcPort() {
        return serverRpcPort;
    }

    public String getServerApiBaseUrl() {
        return serverApiBaseUrl;
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

    public int getWorkersCount() {
        return workersCount;
    }

    public long getMaxPreforkAge() {
        return maxPreforkAge;
    }

    public boolean isDockerOrphanSweeperEnabled() {
        return dockerOrphanSweeperEnabled;
    }

    public long getDockerOrphanSweeperPeriod() {
        return dockerOrphanSweeperPeriod;
    }

    public boolean isDockerOldImageSweeperEnabled() {
        return dockerOldImageSweeperEnabled;
    }

    public long getDockerOldImageSweeperPeriod() {
        return dockerOldImageSweeperPeriod;
    }

    public boolean isRunnerSecurityManagerEnabled() {
        return runnerSecurityManagerEnabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public int getConnectTimeout() {
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

    public Path getMaintenanceModeFile() {
        return maintenanceModeFile;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    private static String getEnv(String key, String defaultValue) {
        String s = System.getenv(key);
        if (Strings.isNullOrEmpty(s)) {
            return defaultValue;
        }
        return s;
    }

    private static Path getDir(String key, String defaultPrefix) throws IOException {
        String s = System.getenv(key);
        if (s == null) {
            return IOUtils.createTempDir(defaultPrefix);
        }

        Path p = Paths.get(s);
        if (!Files.exists(p)) {
            Files.createDirectories(p);
        }
        return p;
    }
}
