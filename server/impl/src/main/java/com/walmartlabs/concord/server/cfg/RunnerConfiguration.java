package com.walmartlabs.concord.server.cfg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Named
@Singleton
public class RunnerConfiguration implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(RunnerConfiguration.class);

    public static final String RUNNER_PATH_KEY = "RUNNER_PATH";
    public static final String DEFAULT_TARGET_NAME = "runner.jar";

    private final Path path;
    private final String targetName;

    public RunnerConfiguration() throws IOException {
        Properties props = new Properties();
        props.load(RunnerConfiguration.class.getResourceAsStream("runner.properties"));
        String defaultPath = props.getProperty("runner.path");

        this.path = Paths.get(Utils.getEnv(RUNNER_PATH_KEY, defaultPath)).toAbsolutePath();
        this.targetName = DEFAULT_TARGET_NAME;
        log.info("init -> path: {}, targetName: {}", path, targetName);
    }

    public RunnerConfiguration(Path path, String targetName) {
        this.path = path;
        this.targetName = targetName;
    }

    public Path getPath() {
        return path;
    }

    public String getTargetName() {
        return targetName;
    }
}
