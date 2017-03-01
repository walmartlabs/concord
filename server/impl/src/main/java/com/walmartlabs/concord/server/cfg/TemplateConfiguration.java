package com.walmartlabs.concord.server.cfg;

import java.io.Serializable;
import java.nio.file.Path;

public class TemplateConfiguration implements Serializable {

    private final Path depsDir;

    public TemplateConfiguration(Path depsDir) {
        this.depsDir = depsDir;
    }

    public Path getDepsDir() {
        return depsDir;
    }
}
