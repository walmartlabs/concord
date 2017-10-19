package com.walmartlabs.concord.agent;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.UUID;

public class Configuration {

    public static final String SERVER_HOST_KEY = "SERVER_HOST";
    public static final String SERVER_RPC_PORT_KEY = "SERVER_RPC_PORT";
    public static final String SERVER_API_PORT_KEY = "SERVER_RPC_PORT";
    public static final String LOG_DIR_KEY = "AGENT_LOG_DIR";
    public static final String PAYLOAD_DIR_KEY = "AGENT_PAYLOAD_DIR";
    public static final String JAVA_CMD_KEY = "AGENT_JAVA_CMD";
    public static final String DEPENDENCY_CACHE_DIR_KEY = "DEPS_CACHE_DIR";
    public static final String RUNNER_PATH = "RUNNER_PATH";
    public static final String WORKERS_COUNT_KEY = "WORKERS_COUNT";
    public static final String MAX_PREFORK_AGE_KEY = "MAX_PREFORK_AGE";
    public static final String DOCKER_SWEEPER_ENABLED_KEY = "DOCKER_SWEEPER_ENABLED";

    private final String agentId;
    private final String serverHost;
    private final int serverRpcPort;
    private final int serverApiPort;
    private final Path logDir;
    private final Path payloadDir;
    private final String agentJavaCmd;
    private final Path dependencyCacheDir;
    private final Path runnerPath;
    private final int workersCount;
    private final long maxPreforkAge;
    private final boolean dockerSweeperEnabled;

    public Configuration() {
        this.agentId = UUID.randomUUID().toString();

        try {
            this.serverHost = getEnv(SERVER_HOST_KEY, "localhost");
            this.serverRpcPort = Integer.parseInt(getEnv(SERVER_RPC_PORT_KEY, "8101"));
            this.serverApiPort = Integer.parseInt(getEnv(SERVER_API_PORT_KEY, "8001"));

            this.logDir = getDir(LOG_DIR_KEY, "logDir");
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

            this.workersCount = Integer.parseInt(getEnv(WORKERS_COUNT_KEY, "2"));

            this.maxPreforkAge = Long.parseLong(getEnv(MAX_PREFORK_AGE_KEY, "30000"));

            this.dockerSweeperEnabled = Boolean.parseBoolean(getEnv(DOCKER_SWEEPER_ENABLED_KEY, "false"));
        } catch (IOException e) {
            throw Throwables.propagate(e);
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

    public int getServerApiPort() {
        return serverApiPort;
    }

    public Path getLogDir() {
        return logDir;
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

    public boolean isDockerSweeperEnabled() {
        return dockerSweeperEnabled;
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
            return Files.createTempDirectory(defaultPrefix);
        }

        Path p = Paths.get(s);
        if (!Files.exists(p)) {
            try {
                Files.createDirectories(p);
            } catch (IOException e) {
                throw new RuntimeException("Can't create a directory: " + p, e);
            }
        }
        return p;
    }
}
