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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.walmartlabs.concord.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class Configuration {

    private static final Logger log = LoggerFactory.getLogger(Configuration.class);

    public static final String AGENT_ID_KEY = "AGENT_ID";
    public static final String SERVER_API_BASE_URL_KEY = "SERVER_API_BASE_URL";
    public static final String SERVER_WEBSOCKET_URL_KEY = "SERVER_WEBSOCKET_URL";
    public static final String LOG_DIR_KEY = "AGENT_LOG_DIR";
    public static final String LOG_MAX_DELAY_KEY = "LOG_MAX_DELAY";
    public static final String PAYLOAD_DIR_KEY = "AGENT_PAYLOAD_DIR";
    public static final String JAVA_CMD_KEY = "AGENT_JAVA_CMD";
    public static final String DEPENDENCY_CACHE_DIR_KEY = "DEPS_CACHE_DIR";
    public static final String RUNNER_PATH = "RUNNER_PATH";
    public static final String WORKERS_COUNT_KEY = "WORKERS_COUNT";
    public static final String MAX_PREFORK_AGE_KEY = "MAX_PREFORK_AGE";
    public static final String MAX_PREFORK_COUNT_KEY = "MAX_PREFORK_COUNT";
    public static final String MAX_WEBSOCKET_INACTIVITY_KEY ="MAX_WEBSOCKET_INACTIVITY";

    public static final String DOCKER_ORPHAN_SWEEPER_ENABLED_KEY = "DOCKER_ORPHAN_SWEEPER_ENABLED";
    public static final String DOCKER_ORPHAN_SWEEPER_PERIOD_KEY = "DOCKER_ORPHAN_SWEEPER_PERIOD";

    public static final String RUNNER_SECURITY_MANAGER_ENABLED_KEY = "RUNNER_SECURITY_MANAGER_ENABLED";

    public static final String API_KEY = "API_KEY";
    public static final String API_VERIFY_SSL_KEY = "API_VERIFY_SSL";
    public static final String CONNECT_TIMEOUT_KEY = "API_CONNECT_TIMEOUT";
    public static final String READ_TIMEOUT_KEY = "API_READ_TIMEOUT";
    public static final String RETRY_COUNT_KEY = "API_RETRY_COUNT";
    public static final String RETRY_INTERVAL_KEY = "API_RETRY_INTERVAL";
    public static final String POLL_INTERVAL_KEY = "QUEUE_POLL_INTERVAL";

    public static final String CAPABILITIES_FILE_KEY = "CAPABILITIES_FILE";
    public static final String USER_AGENT_KEY = "USER_AGENT";
    public static final String STORE_DEPS_DIR_KEY = "STORE_DEPS_DIR";

    public static final String JAVA_PATH_KEY = "JAVA_PATH";

    /**
     * As defined in server/db/src/main/resources/com/walmartlabs/concord/server/db/v0.69.0.xml
     */
    private static final String DEFAULT_AGENT_API_KEY = "O+JMYwBsU797EKtlRQYu+Q";

    private final String agentId;
    private final String serverApiBaseUrl;
    private final Path logDir;
    private final long logMaxDelay;
    private final Path payloadDir;
    private final String agentJavaCmd;
    private final Path dependencyCacheDir;
    private final Path runnerPath;
    private final int workersCount;
    private final long maxPreforkAge;
    private final int maxPreforkCount;
    private final boolean dockerOrphanSweeperEnabled;
    private final long dockerOrphanSweeperPeriod;
    private final boolean runnerSecurityManagerEnabled;

    private final String apiKey;
    private final boolean apiVerifySsl;
    private final int readTimeout;
    private final int connectTimeout;
    private final int retryCount;
    private final long retryInterval;
    private final long pollInterval;

    private final Map<String, Object> capabilities;
    private final String userAgent;

    private final Path dependencyListsDir;

    private final Path javaPath;
    private final String serverWebsocketUrl;
    private final long maxWebSocketInactivity;

    @SuppressWarnings("unchecked")
    public Configuration() {
        try {
            this.agentId = getEnv(AGENT_ID_KEY, UUID.randomUUID().toString());

            this.serverApiBaseUrl = getEnv(SERVER_API_BASE_URL_KEY, "http://localhost:8001");
            this.serverWebsocketUrl = getEnv(SERVER_WEBSOCKET_URL_KEY, serverApiBaseUrl.replace("http", "ws").replace("https", "ws") + "/websocket");
            log.info("Using the API address: {}, {}", serverApiBaseUrl, serverWebsocketUrl);

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
            this.maxPreforkCount = Integer.parseInt(getEnv(MAX_PREFORK_COUNT_KEY, "3"));

            this.dockerOrphanSweeperEnabled = Boolean.parseBoolean(getEnv(DOCKER_ORPHAN_SWEEPER_ENABLED_KEY, "false"));
            this.dockerOrphanSweeperPeriod = Long.parseLong(getEnv(DOCKER_ORPHAN_SWEEPER_PERIOD_KEY, "900000")); // 15 min

            this.runnerSecurityManagerEnabled = Boolean.parseBoolean(getEnv(RUNNER_SECURITY_MANAGER_ENABLED_KEY, "false"));

            this.apiKey = getEnv(API_KEY, DEFAULT_AGENT_API_KEY);
            this.apiVerifySsl = Boolean.parseBoolean(getEnv(API_VERIFY_SSL_KEY, "true"));
            this.connectTimeout = Integer.parseInt(getEnv(CONNECT_TIMEOUT_KEY, "30000"));
            this.readTimeout = Integer.parseInt(getEnv(READ_TIMEOUT_KEY, "60000"));
            this.retryCount = Integer.parseInt(getEnv(RETRY_COUNT_KEY, "5"));
            this.retryInterval = Integer.parseInt(getEnv(RETRY_INTERVAL_KEY, "30000"));
            this.pollInterval = Long.parseLong(getEnv(POLL_INTERVAL_KEY, "2000"));

            String capabilitiesFile = getEnv(CAPABILITIES_FILE_KEY, null);
            if (capabilitiesFile != null) {
                this.capabilities = new ObjectMapper().readValue(new File(capabilitiesFile), Map.class);
                log.info("Using the capabilities: {}", this.capabilities);
            } else {
                this.capabilities = null;
            }
            this.userAgent = getEnv(USER_AGENT_KEY, "Concord-Agent: id=" + agentId);

            this.dependencyListsDir = getDir(STORE_DEPS_DIR_KEY, "dependencyListsDir");
            String java = getEnv(JAVA_PATH_KEY, null);
            if (java != null) {
                this.javaPath = Paths.get(java);
            } else {
                this.javaPath = null;
            }

            this.maxWebSocketInactivity = Long.parseLong(getEnv(MAX_WEBSOCKET_INACTIVITY_KEY, "120000"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getAgentId() {
        return agentId;
    }

    public String getServerApiBaseUrl() {
        return serverApiBaseUrl;
    }

    public String getServerWebsocketUrl() {
        return serverWebsocketUrl;
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

    public int getMaxPreforkCount() {
        return maxPreforkCount;
    }

    public boolean isDockerOrphanSweeperEnabled() {
        return dockerOrphanSweeperEnabled;
    }

    public long getDockerOrphanSweeperPeriod() {
        return dockerOrphanSweeperPeriod;
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
