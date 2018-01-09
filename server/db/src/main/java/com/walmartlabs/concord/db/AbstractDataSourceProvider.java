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

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import javax.sql.DataSource;

public abstract class AbstractDataSourceProvider implements Provider<DataSource> {

    private static final Logger log = LoggerFactory.getLogger(AbstractDataSourceProvider.class);

    private final String url;
    private final String driverClassName;
    private final String username;
    private final String password;
    private final int maxPoolSize;

    public AbstractDataSourceProvider(String url, String driverClassName,
                                      String username, String password,
                                      int maxPoolSize) {

        this.url = url;
        this.driverClassName = driverClassName;
        this.username = username;
        this.password = password;
        this.maxPoolSize = maxPoolSize;
    }

    @Override
    public DataSource get() {
        log.info("get -> creating a new datasource...");

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setDriverClassName(driverClassName);
        ds.setUsername(username);
        ds.setPassword(password);

        ds.setAutoCommit(false);
        ds.setMaxLifetime(Long.MAX_VALUE);

        ds.setMinimumIdle(1);
        ds.setMaximumPoolSize(maxPoolSize);

        ds.setLeakDetectionThreshold(10000);

        return ds;
    }
}
