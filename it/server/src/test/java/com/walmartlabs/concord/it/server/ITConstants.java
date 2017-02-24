package com.walmartlabs.concord.it.server;

import java.io.IOException;
import java.util.Properties;

public final class ITConstants {

    public static final String SERVER_URL;
    public static final String DEPENDENCIES_DIR;
    public static final String TEMPLATES_DIR;
    public static final int GIT_SERVER_PORT;
    public static final String GIT_SERVER_URL;

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

        port = 8022;
        try {
            port = Integer.parseInt(System.getenv("IT_GIT_SERVER_PORT"));
        } catch (NumberFormatException e) {
        }

        String host = props.getProperty("docker.host.addr");
        if (host == null || host.trim().isEmpty()) {
            host = "localhost";
        }

        GIT_SERVER_PORT = port;
        GIT_SERVER_URL = "ssh://git@" + host + ":" + GIT_SERVER_PORT + "/";
    }

    private ITConstants() {
    }
}
