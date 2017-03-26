package com.walmartlabs.concord.server.cfg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named
@Singleton
public class LogStoreConfiguration implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(LogStoreConfiguration.class);

    public static final String LOG_STORE_DIR_KEY = "LOG_STORE_DIR";

    private final Path baseDir;

    public LogStoreConfiguration() throws IOException {
        String s = System.getenv(LOG_STORE_DIR_KEY);
        this.baseDir = s != null ? Paths.get(s).toAbsolutePath() : Files.createTempDirectory("logs");
        log.info("init -> baseDir: {}", baseDir);
    }

    public LogStoreConfiguration(Path baseDir) {
        this.baseDir = baseDir;
    }

    public Path getBaseDir() {
        return baseDir;
    }
}
