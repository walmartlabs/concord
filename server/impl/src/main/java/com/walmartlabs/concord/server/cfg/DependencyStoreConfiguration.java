package com.walmartlabs.concord.server.cfg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named
@Singleton
public class DependencyStoreConfiguration implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(DependencyStoreConfiguration.class);

    public static final String DEPS_STORE_DIR_KEY = "DEPS_STORE_DIR";

    private final Path depsDir;

    public DependencyStoreConfiguration() {
        String s = System.getenv(DEPS_STORE_DIR_KEY);
        if (s == null) {
            log.warn("init -> {} must be set in order to use project templates and custom process dependencies", DEPS_STORE_DIR_KEY);
        }

        this.depsDir = s != null ? Paths.get(s).toAbsolutePath() : null;
        log.info("init -> depsDir: {}", depsDir);
    }

    public DependencyStoreConfiguration(Path depsDir) {
        this.depsDir = depsDir;
    }

    public Path getDepsDir() {
        return depsDir;
    }
}
