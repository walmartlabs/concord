package com.walmartlabs.concord.agent;

import java.io.Serializable;
import java.nio.file.Path;

public class Configuration implements Serializable {

    private final Path logStore;

    public Configuration(Path logStore) {
        this.logStore = logStore;
    }

    public Path getLogStore() {
        return logStore;
    }
}
