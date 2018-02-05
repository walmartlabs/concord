package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.google.common.base.Strings;

public final class ITConstants {

    public static final String PROJECT_VERSION;
    public static final String SERVER_URL;
    public static final String DEPENDENCIES_DIR;
    public static final String GIT_SERVER_URL_PATTERN;
    public static final String SMTP_SERVER_HOST;
    public static final String DOCKER_ANSIBLE_IMAGE;

    static {
        PROJECT_VERSION = env("IT_PROJECT_VERSION", "LATEST");

        SERVER_URL = "http://localhost:" + env("IT_SERVER_PORT", "8001");
        DEPENDENCIES_DIR = System.getenv("IT_DEPS_DIR");

        String dockerAddr = env("IT_DOCKER_HOST_ADDR", "127.0.0.1");
        String gitHost = dockerAddr != null ? dockerAddr : "localhost";
        GIT_SERVER_URL_PATTERN = "ssh://git@" + gitHost + ":%d/";

        SMTP_SERVER_HOST = dockerAddr;

        DOCKER_ANSIBLE_IMAGE = env("IT_DOCKER_ANSIBLE_IMAGE", "walmartlabs/concord-ansible");
    }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        if (Strings.isNullOrEmpty(v)) {
            return def;
        }
        return v;
    }

    private ITConstants() {
    }
}
