package com.walmartlabs.concord.it.console;

public final class ITConstants {

    public static final int LOCAL_CONSOLE_PORT = 3000;
    public static final int REMOTE_CONSOLE_PORT = 8080;

    public static final int SERVER_PORT;
    public static final int SELENIUM_PORT;

    public static final String SERVER_URL;

    public static final String WEBDRIVER_TYPE;
    public static final String SCREENSHOTS_DIR;

    public static final String REMOTE_USER = "_test";
    public static final String REMOTE_PASSWORD = "_q1";

    static {
        SERVER_PORT = Integer.parseInt(env("IT_SERVER_PORT", "8001"));
        SERVER_URL = "http://localhost:" + SERVER_PORT;

        SELENIUM_PORT = Integer.parseInt(env("IT_SELENIUM_PORT", "4444"));
        WEBDRIVER_TYPE = env("IT_WEBDRIVER_TYPE", "local");
        SCREENSHOTS_DIR = env("IT_SCREENSHOTS_DIR", "target/screenshots");
    }

    private ITConstants() {
    }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        if (v == null) {
            return def;
        }
        return v;
    }
}
