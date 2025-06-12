package com.walmartlabs.concord.server.plugins.noderoster.db;

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

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.walmartlabs.concord.db.DataSourceUtils;
import com.walmartlabs.concord.db.DatabaseChangeLogProvider;
import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.db.MainDB;
import org.jooq.Configuration;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.sql.DataSource;

@Named
public class DatabaseModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DatabaseChangeLogProvider.class).annotatedWith(NodeRosterDB.class).to(NodeRosterDBChangeLogProvider.class);
    }

    @Provides
    @NodeRosterDB
    @Singleton
    public DataSource dataSource(@NodeRosterDB DatabaseConfiguration cfg,
                                 MetricRegistry metricRegistry,
                                 @NodeRosterDB DatabaseChangeLogProvider changeLogProvider,
                                 @MainDB DatabaseConfiguration mainCfg) {

        DataSourceUtils.migrateDb(mainCfg, changeLogProvider);
        return DataSourceUtils.createDataSource(cfg, "noderoster", cfg.username(), cfg.password(), metricRegistry);
    }

    @Provides
    @NodeRosterDB
    @Singleton
    public Configuration jooqConfiguration(@NodeRosterDB DataSource ds) {
        return DataSourceUtils.createJooqConfiguration(ds);
    }
}
