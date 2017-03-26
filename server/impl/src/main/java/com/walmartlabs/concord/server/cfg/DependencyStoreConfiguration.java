package com.walmartlabs.concord.server.cfg;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Named
@Singleton
public class DependencyStoreConfiguration implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(DependencyStoreConfiguration.class);

    public static final String DEPS_STORE_DIR_KEY = "DEPS_STORE_DIR";

    private static final Path DEFAULT_DIRECTORY;

    static {
        Properties props = new Properties();
        try {
            props.load(DependencyStoreConfiguration.class.getResourceAsStream("maven.properties"));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        String s = props.getProperty("depsDir");
        if (s == null || s.trim().isEmpty()) {
            s = System.getProperty("user.dir");
        }

        DEFAULT_DIRECTORY = Paths.get(s);
    }

    private final Path depsDir;

    public DependencyStoreConfiguration() {
        String s = System.getenv(DEPS_STORE_DIR_KEY);
        this.depsDir = s != null ? Paths.get(s).toAbsolutePath() : DEFAULT_DIRECTORY;
        log.info("init -> depsDir: {}", depsDir);
    }

    public DependencyStoreConfiguration(Path depsDir) {
        this.depsDir = depsDir;
    }

    public Path getDepsDir() {
        return depsDir;
    }
}
