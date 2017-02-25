package com.walmartlabs.concord.server.cfg;

import java.io.Serializable;
import java.nio.file.Path;

public class AttachmentStoreConfiguration implements Serializable {

    private final Path baseDir;

    public AttachmentStoreConfiguration(Path baseDir) {
        this.baseDir = baseDir;
    }

    public Path getBaseDir() {
        return baseDir;
    }
}
