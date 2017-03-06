package com.walmartlabs.concord.server.cfg;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Named
@Singleton
public class RunnerConfigurationProvider implements Provider<RunnerConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(RunnerConfigurationProvider.class);

    public static final String RUNNER_PATH_KEY = "RUNNER_PATH";
    public static final String DEFAULT_TARGET_NAME = "runner.jar";

    @Override
    public RunnerConfiguration get() {
        String defaultPath;
        try {
            Properties props = new Properties();
            props.load(RunnerConfiguration.class.getResourceAsStream("runner.properties"));
            defaultPath = props.getProperty("runner.path");
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        Path p = Paths.get(Utils.getEnv(RUNNER_PATH_KEY, defaultPath)).toAbsolutePath();
        log.info("get -> runner's path: {}", p);

        return new RunnerConfiguration(p, DEFAULT_TARGET_NAME);
    }
}
