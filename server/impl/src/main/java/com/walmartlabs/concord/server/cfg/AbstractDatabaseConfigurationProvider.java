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

import com.walmartlabs.concord.db.DatabaseConfiguration;
import org.jooq.SQLDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;

public abstract class AbstractDatabaseConfigurationProvider implements Provider<DatabaseConfiguration> {

    private final Logger log;

    public static final String DB_DIALECT_KEY = "DB_DIALECT";
    public static final String DEFAULT_DB_DIALECT = "POSTGRES";

    public static final String DB_DRIVER_KEY = "DB_DRIVER";
    public static final String DEFAULT_DB_DRIVER = "org.postgresql.Driver";

    public static final String DB_URL_KEY = "DB_URL";
    public static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/postgres";

    public static final String DEFAULT_DB_MAX_POOL_SIZE = "10";

    private final String userNameKey;
    private final String defaultUserName;
    private final String passwordKey;
    private final String defaultPassword;
    private final String maxPoolSizeKey;

    public AbstractDatabaseConfigurationProvider(String userNameKey, String defaultUserName,
                                                 String passwordKey, String defaultPassword,
                                                 String maxPoolSizeKey) {

        this.userNameKey = userNameKey;
        this.defaultUserName = defaultUserName;
        this.passwordKey = passwordKey;
        this.defaultPassword = defaultPassword;
        this.maxPoolSizeKey = maxPoolSizeKey;

        this.log = LoggerFactory.getLogger(this.getClass());
    }

    @Override
    public DatabaseConfiguration get() {
        String dialect = Utils.getEnv(DB_DIALECT_KEY, DEFAULT_DB_DIALECT).toUpperCase();
        String driverClassName = Utils.getEnv(DB_DRIVER_KEY, DEFAULT_DB_DRIVER);
        String url = Utils.getEnv(DB_URL_KEY, DEFAULT_DB_URL);
        String username = Utils.getEnv(userNameKey, defaultUserName);
        String password = Utils.getEnv(passwordKey, defaultPassword);
        int maxPoolSize = Integer.parseInt(Utils.getEnv(maxPoolSizeKey, DEFAULT_DB_MAX_POOL_SIZE));

        log.info("get -> using: {} - {}@{}, maxPoolSize={}", dialect, username, url, maxPoolSize);
        return new DatabaseConfiguration(SQLDialect.valueOf(dialect), driverClassName, url,
                username, password, maxPoolSize);
    }
}
