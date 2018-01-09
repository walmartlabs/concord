package com.walmartlabs.concord.server.cfg;

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

import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class DatabaseConfigurationProvider extends AbstractDatabaseConfigurationProvider {

    public static final String DB_USERNAME_KEY = "DB_USERNAME";
    public static final String DEFAULT_DB_USERNAME = "postgres";

    public static final String DB_PASSWORD_KEY = "DB_PASSWORD";
    public static final String DEFAULT_DB_PASSWORD = "q1";

    public static final String DB_MAX_POOL_SIZE_KEY = "DB_MAX_POOL_SIZE";

    public DatabaseConfigurationProvider() {
        super(DB_USERNAME_KEY, DEFAULT_DB_USERNAME, DB_PASSWORD_KEY, DEFAULT_DB_PASSWORD, DB_MAX_POOL_SIZE_KEY);
    }
}
