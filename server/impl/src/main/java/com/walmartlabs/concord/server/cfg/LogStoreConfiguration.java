package com.walmartlabs.concord.server.cfg;

import java.io.Serializable;
import java.nio.file.Path;

public class LogStoreConfiguration implements Serializable {

    private final Path baseDir;

    public LogStoreConfiguration(Path baseDir) {
        this.baseDir = baseDir;
    }

    public Path getBaseDir() {
        return baseDir;
    }
}
