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
public class RepositoryConfiguration implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(RepositoryConfiguration.class);

    public static final String REPO_CACHE_DIR_KEY = "REPO_CACHE_DIR";

    private final Path repoCacheDir;

    public RepositoryConfiguration() throws IOException {
        String s = System.getenv(REPO_CACHE_DIR_KEY);
        this.repoCacheDir = s != null ? Paths.get(s).toAbsolutePath() : Files.createTempDirectory("repos");
        log.info("init -> repoCacheDir: {}", repoCacheDir);
    }

    public Path getRepoCacheDir() {
        return repoCacheDir;
    }
}
