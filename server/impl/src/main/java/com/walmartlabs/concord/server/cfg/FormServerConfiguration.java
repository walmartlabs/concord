package com.walmartlabs.concord.server.cfg;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named
@Singleton
public class FormServerConfiguration {

    public static final String FORM_SERVER_DIR_KEY = "FORM_SERVER_DIR";

    private final Path baseDir;

    public FormServerConfiguration() throws IOException {
        String s = System.getenv(FORM_SERVER_DIR_KEY);
        this.baseDir = s != null ? Paths.get(s) : Files.createTempDirectory("formserv");
    }

    public Path getBaseDir() {
        return baseDir;
    }
}
