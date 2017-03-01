package com.walmartlabs.concord.server.cfg;

import java.io.Serializable;
import java.nio.file.Path;

public class DependencyStoreConfiguration implements Serializable {

    private final Path depsDir;

    public DependencyStoreConfiguration(Path depsDir) {
        this.depsDir = depsDir;
    }

    public Path getDepsDir() {
        return depsDir;
    }
}
