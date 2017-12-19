package com.walmartlabs.concord.server.cfg;

import java.util.Properties;

public final class Utils {

    public static String getEnv(String key, String defaultValue) {
        String s = System.getenv(key);
        if (s == null) {
            return defaultValue;
        }
        return s;
    }

    public static long getLong(Properties props, String key, long defaultValue) {
        String s = props.getProperty(key);
        if (s == null) {
            return defaultValue;
        }
        return Long.parseLong(s);
    }

    private Utils() {
    }

    public static boolean getBoolean(Properties props, String key, boolean defaultValue) {
        String s = props.getProperty(key);
        if (s == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(s);
    }
}
