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

        SERVER_URL = "http://localhost:" + parseInt(props, "server.port", 8001);

        DEPENDENCIES_DIR = props.getProperty("deps.dir");
        TEMPLATES_DIR = props.getProperty("templates.dir");

        String dockerAddr = nil(props.getProperty("docker.host.addr"));

        String gitHost = dockerAddr != null ? dockerAddr : "localhost";
        GIT_SERVER_PORT = parseInt(props, "git.server.port", 8022);
        GIT_SERVER_URL = "ssh://git@" + gitHost + ":" + GIT_SERVER_PORT + "/";
    }

    private static int parseInt(Properties props, String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty("server.port"));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String nil(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return s;
    }

    private ITConstants() {
    }
}
