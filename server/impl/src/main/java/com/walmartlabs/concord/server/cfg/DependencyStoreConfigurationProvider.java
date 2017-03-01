package com.walmartlabs.concord.server.cfg;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Named
@Singleton
public class DependencyStoreConfigurationProvider implements Provider<DependencyStoreConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(DependencyStoreConfigurationProvider.class);

    public static final String DEPS_STORE_DIR_KEY = "DEPS_STORE_DIR";

    private static final Path DEFAULT_REPO;

    static {
        Properties props = new Properties();
        try {
            props.load(DependencyStoreConfigurationProvider.class.getResourceAsStream("maven.properties"));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        String s = props.getProperty("localRepository");
        if (s == null || s.trim().isEmpty()) {
            s = System.getProperty("user.home") + "/.m2/repository";
        }

        DEFAULT_REPO = Paths.get(s);
    }

    @Override
    public DependencyStoreConfiguration get() {
        String s = System.getenv(DEPS_STORE_DIR_KEY);
        Path p = s != null ? Paths.get(s).toAbsolutePath() : DEFAULT_REPO;
        log.info("get -> using '{}' as the source of template dependencies", p);
        return new DependencyStoreConfiguration(p);
    }
}
