package com.walmartlabs.concord.plugins.noderoster;

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


public final class Constants {

    public static final int RETRY_COUNT = 3;
    public static final long RETRY_INTERVAL = 5000;

    public static final String ACTION_KEY = "action";
    public static final String BASE_URL_KEY = "baseUrl";
    public static final String HOSTNAME_KEY = "hostName";
    public static final String HOSTID_KEY = "hostId";
    public static final String ARTIFACT_PATTERN_KEY = "artifactPattern";
    public static final String PROJECT_ID_KEY = "projectId";
    public static final String LIMIT_KEY = "limit";
    public static final String OFFSET_KEY = "offset";
    public static final String API_KEY = "apiKey";

    public static final String[] ALL_IN_PARAMS = {
            ACTION_KEY,
            BASE_URL_KEY,
            HOSTNAME_KEY,
            HOSTID_KEY,
            ARTIFACT_PATTERN_KEY,
            PROJECT_ID_KEY,
            LIMIT_KEY,
            OFFSET_KEY,
            API_KEY
    };

    private Constants() {
    }
}
