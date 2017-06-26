package com.walmartlabs.concord.server.cfg;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named
@Singleton
public class TemplateConfiguration {

    public static final String CACHE_DIR_KEY = "TEMPLATE_CACHE_DIR";

    private final Path cacheDir;

    public TemplateConfiguration() throws IOException {
        String s = System.getenv(CACHE_DIR_KEY);
        this.cacheDir = s != null ? Paths.get(s) : Files.createTempDirectory("templateCache");
    }

    public Path getCacheDir() {
        return cacheDir;
    }
}
