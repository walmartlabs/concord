package com.walmartlabs.concord.server.cfg;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named
@Singleton
public class LogStoreConfigurationProvider implements Provider<LogStoreConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(LogStoreConfigurationProvider.class);

    public static final String LOG_STORE_DIR_KEY = "LOG_STORE_DIR";

    @Override
    public LogStoreConfiguration get() {
        try {
            String s = System.getenv(LOG_STORE_DIR_KEY);
            Path p = s != null ? Paths.get(s).toAbsolutePath() : Files.createTempDirectory("logs");
            log.info("get -> using '{}' as log storage", p);
            return new LogStoreConfiguration(p);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
