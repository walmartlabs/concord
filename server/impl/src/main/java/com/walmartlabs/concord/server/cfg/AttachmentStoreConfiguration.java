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
public class AttachmentStoreConfiguration implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStoreConfiguration.class);

    public static final String ATTACHMENT_STORE_DIR_KEY = "ATTACHMENT_STORE_DIR";

    private final Path baseDir;

    public AttachmentStoreConfiguration() throws IOException {
        String s = System.getenv(ATTACHMENT_STORE_DIR_KEY);
        this.baseDir = s != null ? Paths.get(s).toAbsolutePath() : Files.createTempDirectory("attachments");
        log.info("init -> baseDir: {}", baseDir);
    }

    public AttachmentStoreConfiguration(Path baseDir) {
        this.baseDir = baseDir;
    }

    public Path getBaseDir() {
        return baseDir;
    }
}
