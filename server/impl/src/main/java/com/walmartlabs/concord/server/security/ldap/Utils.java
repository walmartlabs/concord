package com.walmartlabs.concord.server.security.ldap;

public class Utils {
    public static final int maxLevel = 20;

    public static void recursionLimiter() {
        if (maxLevel == 0)
            return;
        try {
            throw new IllegalStateException("Too deep, recursion limit reached, emerging");
        } catch (IllegalStateException e) {
            StackTraceElement[] stackTraces = e.getStackTrace();
            if (stackTraces.length > 1) {
                StackTraceElement recursiveElement = stackTraces[1];
                int depth = 1;
                for (; depth < stackTraces.length; depth++) {
                    if (!(stackTraces[depth].getClassName().equals(recursiveElement.getClassName())
                            && stackTraces[depth].getMethodName().equals(recursiveElement.getMethodName()))) {
                        break;
                    }
                }
                if (depth > maxLevel + 1)
                    throw e;
            }
        }
    }
}
