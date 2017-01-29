package com.walmartlabs.concord.server.cfg;

public final class Utils {

    public static String getEnv(String key, String defaultValue) {
        String s = System.getenv(key);
        if (s == null) {
            return defaultValue;
        }
        return s;
    }

    private Utils() {
    }
}
