package com.walmartlabs.concord.server;

public final class Utils {

    public static String[] toString(Enum<?>... e) {
        String[] as = new String[e.length];
        for (int i = 0; i < e.length; i++) {
            as[i] = e[i].toString();
        }
        return as;
    }

    private Utils() {
    }
}
