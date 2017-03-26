package com.walmartlabs.concord.server.cfg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named
@Singleton
public class PayloadStoreConfiguration {

    public static final Logger log = LoggerFactory.getLogger(PayloadStoreConfiguration.class);

    public static final String PAYLOAD_DIR_KEY = "PAYLOAD_DIR";

    private final Path baseDir;

    public PayloadStoreConfiguration() throws IOException {
        String s = System.getenv(PAYLOAD_DIR_KEY);
        this.baseDir = s != null ? Paths.get(s) : Files.createTempDirectory("payload");
    }

    public PayloadStoreConfiguration(Path baseDir) {
        this.baseDir = baseDir;
    }

    public Path getBaseDir() {
        return baseDir;
    }
}
