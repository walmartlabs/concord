package com.walmartlabs.concord.db;

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
import com.walmartlabs.concord.db.migration.MigrationTask;
import org.jooq.Configuration;

import javax.inject.Singleton;
import javax.sql.DataSource;
import java.util.Comparator;
import java.util.Set;

public class DatabaseModule extends AbstractModule {

    private final boolean migrateDb;

    public DatabaseModule() {
        this(true);
    }

    public DatabaseModule(boolean migrateDb) {
        this.migrateDb = migrateDb;
    }

    @Override
    protected void configure() {
    }

    @Provides
    @MainDB
    @Singleton
    public DataSource appDataSource(@MainDB DatabaseConfiguration cfg,
                                    MetricRegistry metricRegistry,
                                    Set<DatabaseChangeLogProvider> changeLogProviders,
                                    Set<MigrationTask> migrationTasks) {

        DataSource ds = DataSourceUtils.createDataSource(cfg, "app", cfg.username(), cfg.password(), metricRegistry);

        if (migrateDb) {
            changeLogProviders.stream()
                    // can't inject a set of objects with the same qualifier, filter manually
                    .filter(p -> p.getClass().getAnnotation(MainDB.class) != null)
                    .sorted(Comparator.comparingInt(DatabaseChangeLogProvider::order))
                    .forEach(p -> DataSourceUtils.migrateDb(ds, p, cfg.changeLogParameters()));
            migrationTasks.stream().forEach(migrationTask -> migrationTask.execute(ds));
        }

        return ds;
    }

    @Provides
    @JsonStorageDB
    @Singleton
    public DataSource inventoryDataSource(@JsonStorageDB DatabaseConfiguration cfg, MetricRegistry metricRegistry) {
        return DataSourceUtils.createDataSource(cfg, "inventory", cfg.username(), cfg.password(), metricRegistry);
    }

    @Provides
    @MainDB
    @Singleton
    public Configuration appJooqConfiguration(@MainDB DataSource ds) {
        return DataSourceUtils.createJooqConfiguration(ds);
    }

    @Provides
    @JsonStorageDB
    @Singleton
    public Configuration inventoryJooqConfiguration(@JsonStorageDB DataSource ds) {
        return DataSourceUtils.createJooqConfiguration(ds);
    }
}
