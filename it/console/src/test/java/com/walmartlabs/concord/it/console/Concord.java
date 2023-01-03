package com.walmartlabs.concord.it.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

public final class Concord {

    public static final String ADMIN_API_KEY = getApiKey();

    private Concord() {
    }

    private static String getApiKey() {
        String s = System.getenv("IT_DEFAULT_API_KEY");
        if (s == null) {
            throw new IllegalStateException("The default (admin) API key must be configured via IT_DEFAULT_API_KEY environment variable. " +
                    "The value must match the db.changeLogParameters.defaultAdminToken value in the server's configuration file");
        }
        return s;
    }
}
