package com.walmartlabs.concord.dependencymanager;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public record Version(String version) {

    private static final Version INSTANCE;

    static {
        Properties props = new Properties();

        try (InputStream in = Version.class.getResourceAsStream("version.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("version load error", e);
        }

        String version = props.getProperty("version");

        INSTANCE = new Version(version);
    }

    public static String get() {
        return INSTANCE.version();
    }
}
