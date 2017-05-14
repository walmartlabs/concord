package com.walmartlabs.concord.agent;

import com.google.common.base.Supplier;
import com.google.common.base.Throwables;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfigurationProvider implements Supplier<Configuration> {

    public static final String SERVER_HOST_KEY = "SERVER_HOST";
    public static final String SERVER_PORT_KEY = "SERVER_PORT";
    public static final String LOG_DIR_KEY = "AGENT_LOG_DIR";
    public static final String PAYLOAD_DIR_KEY = "AGENT_PAYLOAD_DIR";
    public static final String JAVA_CMD_KEY = "AGENT_JAVA_CMD";
    public static final String DEPENDENCY_CACHE_DIR_KEY = "DEPS_CACHE_DIR";
    public static final String RUNNER_PATH = "RUNNER_PATH";

    @Override
    public Configuration get() {
        try {
            String serverHost = getEnv(SERVER_HOST_KEY, "localhost");
            int serverPort = Integer.parseInt(getEnv(SERVER_PORT_KEY, "8101"));

            Path logDir = getDir(LOG_DIR_KEY, "logDir");
            Path payloadDir = getDir(PAYLOAD_DIR_KEY, "payloadDir");
            String agentJavaCmd = getEnv(JAVA_CMD_KEY, "java");
            Path dependencyCacheDir = getDir(DEPENDENCY_CACHE_DIR_KEY, "depsCacheDir");

            String s = System.getenv(RUNNER_PATH);
            if (s == null) {
                Properties props = new Properties();
                props.load(ConfigurationProvider.class.getResourceAsStream("runner.properties"));
                s = props.getProperty("runner.path");
            }

            return new Configuration(serverHost, serverPort, logDir, payloadDir, agentJavaCmd, dependencyCacheDir, Paths.get(s));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private static String getEnv(String key, String defaultValue) {
        String s = System.getenv(key);
        if (s == null) {
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
