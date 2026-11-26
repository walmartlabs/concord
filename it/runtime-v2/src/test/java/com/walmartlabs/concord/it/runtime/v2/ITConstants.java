package com.walmartlabs.concord.it.runtime.v2;

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

import com.google.common.base.Strings;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ITConstants {

    public static final String PROJECT_VERSION;
    public static final String DOCKER_ANSIBLE_IMAGE;
    public static final long DEFAULT_TEST_TIMEOUT = 120000;

    static {
        PROJECT_VERSION = getProperties("version.properties").getProperty("project.version");

        DOCKER_ANSIBLE_IMAGE = env("IT_DOCKER_ANSIBLE_IMAGE", "walmartlabs/concord-ansible");
    }

    private static Properties getProperties(String path) {
        try (InputStream in = ClassLoader.getSystemResourceAsStream(path)) {
            Properties props = new Properties();
            props.load(in);
            return props;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
