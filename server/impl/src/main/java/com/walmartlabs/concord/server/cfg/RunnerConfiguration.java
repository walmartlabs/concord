package com.walmartlabs.concord.server.cfg;

import java.io.Serializable;
import java.nio.file.Path;

public class RunnerConfiguration implements Serializable {

    private final Path path;
    private final String targetName;

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
