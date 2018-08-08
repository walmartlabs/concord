package com.walmartlabs.concord.server.metrics;

public class MetricUtils {

    private static final String SHARED_PREFIX = "com.walmartlabs.concord.";

    public static String createFqn(String type, Class<?> owner, String name, String suffix) {
        String n = owner.getName();
        if (n.startsWith(SHARED_PREFIX)) {
            n = n.substring(SHARED_PREFIX.length());
        }
        return type + "," + n + "." + name + (suffix != null ? suffix : "");
    }
}
