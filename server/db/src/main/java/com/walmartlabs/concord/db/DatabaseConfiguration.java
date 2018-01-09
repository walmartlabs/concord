package com.walmartlabs.concord.db;

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

import org.jooq.SQLDialect;

import java.io.Serializable;

public class DatabaseConfiguration implements Serializable {

    private final SQLDialect dialect;
    private final String driverClassName;
    private final String url;
    private final String username;
    private final String password;
    private final int maxPoolSize;

    public DatabaseConfiguration(SQLDialect dialect, String driverClassName, String url,
                                 String username, String password,
                                 int maxPoolSize) {

        this.dialect = dialect;
        this.driverClassName = driverClassName;
        this.url = url;
        this.username = username;
        this.password = password;
        this.maxPoolSize = maxPoolSize;
    }

    public SQLDialect getDialect() {
        return dialect;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }
}
