package com.walmartlabs.concord.server.cfg;

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

import java.io.Serializable;

/**
 * These configuration parameters cannot be put into the configuration file as
 * they are needed before {@link com.walmartlabs.ollie.OllieServer} is
 * instantiated.
 */
public class ServerConfiguration implements Serializable {

    private static final String SECURE_COOKIES_KEY = "SECURE_COOKIES";
    private static final String SESSION_TIMEOUT_KEY = "SESSION_TIMEOUT";

    public static final boolean secureCookies;
    public static final int sessionTimeout;

    static {
        secureCookies = Boolean.parseBoolean(getEnv(SECURE_COOKIES_KEY, "false"));
        sessionTimeout = Integer.parseInt(getEnv(SESSION_TIMEOUT_KEY, "36000")); // 10 hrs
    }

    public boolean isSecureCookies() {
        return secureCookies;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }

    private static String getEnv(String k, String defaultValue) {
        String v = System.getenv(k);
        if (v == null) {
            return defaultValue;
        }
        return v;
    }
}
