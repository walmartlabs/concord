package com.walmartlabs.concord.it.server;

import java.io.IOException;
import java.util.Properties;

public final class ITConstants {

    public static final String SERVER_URL;
    public static final String DEPENDENCIES_DIR;
    public static final String TEMPLATES_DIR;

    static {
        Properties props = new Properties();
        try {
            props.load(ClassLoader.getSystemResourceAsStream("test.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int port = 8001;
        try {
            port = Integer.parseInt(System.getenv("IT_SERVER_PORT"));
        } catch (NumberFormatException e) {
        }

        SERVER_URL = "http://localhost:" + port;
        DEPENDENCIES_DIR = props.getProperty("deps.dir");
        TEMPLATES_DIR = props.getProperty("templates.dir");
    }

    private ITConstants() {
    }
}
