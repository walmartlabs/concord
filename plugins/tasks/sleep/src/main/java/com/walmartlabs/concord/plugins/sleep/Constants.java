package com.walmartlabs.concord.plugins.sleep;

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

public class Constants {

    public static final int RETRY_COUNT = 3;
    public static final long RETRY_INTERVAL = 5000;

    public static final String DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSX";
    public static final String DURATION_KEY = "duration";
    public static final String SUSPEND_KEY = "suspend";
    public static final String UNTIL_KEY = "until";

    public static final String[] ALL_IN_PARAMS = {
            DURATION_KEY,
            SUSPEND_KEY,
            UNTIL_KEY
    };

    private Constants() {
    }
}