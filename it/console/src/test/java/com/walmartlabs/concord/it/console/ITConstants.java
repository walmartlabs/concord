package com.walmartlabs.concord.it.console;

import java.util.Properties;

public final class ITConstants {

    public static final int LOCAL_CONSOLE_PORT = 3000;
    public static final int REMOTE_CONSOLE_PORT = 8080;

    public static final int SERVER_PORT;
    public static final int SELENIUM_PORT;
    public static final String WEBDRIVER_TYPE;
    public static final String SCREENSHOTS_DIR;

    static {
        try {
            Properties props = new Properties();
            props.load(ClassLoader.getSystemResourceAsStream("test.properties"));

            SERVER_PORT = parseInt(props.getProperty("server.port"), 8001);
            SELENIUM_PORT = parseInt(props.getProperty("selenium.port"), 4444);
            WEBDRIVER_TYPE = props.getProperty("webdriver.type", "local");
            SCREENSHOTS_DIR = props.getProperty("screenshots.dir", "target/screenshots");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ITConstants() {
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
