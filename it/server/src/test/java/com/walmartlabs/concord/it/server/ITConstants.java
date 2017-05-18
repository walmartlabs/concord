package com.walmartlabs.concord.it.server;

import com.google.common.base.Strings;

public final class ITConstants {

    public static final String SERVER_URL;
    public static final String DEPENDENCIES_DIR;
    public static final String GIT_SERVER_URL_PATTERN;
    public static final String SMTP_SERVER_HOST;

    static {
        SERVER_URL = "http://localhost:" + env("IT_SERVER_PORT", "8001");
        DEPENDENCIES_DIR = System.getenv("IT_DEPS_DIR");

        String dockerAddr = env("IT_DOCKER_HOST_ADDR", "127.0.0.1");
        String gitHost = dockerAddr != null ? dockerAddr : "localhost";
        GIT_SERVER_URL_PATTERN = "ssh://git@" + gitHost + ":%d/";

        SMTP_SERVER_HOST = dockerAddr;
    }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        if (Strings.isNullOrEmpty(v)) {
            return def;
        }
        return v;
    }

    private ITConstants() {
    }
}
