package com.walmartlabs.concord.agent;

import com.google.common.base.Throwables;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named
@Singleton
public class ConfigurationProvider implements Provider<Configuration> {

    public static final String LOG_DIR_KEY = "AGENT_LOG_DIR";
    public static final String PAYLOAD_DIR_KEY = "AGENT_PAYLOAD_DIR";
    public static final String JAVA_CMD_KEY = "AGENT_JAVA_CMD";

    @Override
    public Configuration get() {
        try {
            Path logDir = getDir(LOG_DIR_KEY, "logDir");
            Path payloadDir = getDir(PAYLOAD_DIR_KEY, "payloadDir");
            String agentJavaCmd = getEnv(JAVA_CMD_KEY, "java");
            return new Configuration(logDir, payloadDir, agentJavaCmd);
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
