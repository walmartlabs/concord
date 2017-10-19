package com.walmartlabs.concord.runner;

public final class ConfigurationUtils {

    public static String getEnv(String key, String def) {
        String value = System.getProperty(key);
        if (value != null) {
            return value;
        }
        if (def != null) {
            return def;
        }
        throw new IllegalArgumentException(key + " must be specified");
    }

    private ConfigurationUtils() {
    }
}
