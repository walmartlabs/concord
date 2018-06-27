package com.walmartlabs.concord.it.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */


public final class ITConstants {

    public static final int LOCAL_CONSOLE_PORT = 3000;
    public static final int REMOTE_CONSOLE_PORT = 8080;

    public static final int SELENIUM_PORT;

    public static final String WEBDRIVER_TYPE;
    public static final String SCREENSHOTS_DIR;

    static {
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
