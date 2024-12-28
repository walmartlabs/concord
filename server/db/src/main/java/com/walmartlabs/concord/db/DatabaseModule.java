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
import com.google.inject.Binder;
import com.google.inject.Provides;
import org.jooq.Configuration;

import javax.inject.Singleton;
import javax.sql.DataSource;
import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class DatabaseModule implements com.google.inject.Module {

    private final boolean migrateDb;

    public DatabaseModule() {
        this(true);
    }

    public DatabaseModule(boolean migrateDb) {
        this.migrateDb = migrateDb;
    }

    @Override
    public void configure(Binder binder) {
        newSetBinder(binder, DatabaseChangeLogProvider.class).addBinding().to(MainDBChangeLogProvider.class);
        newSetBinder(binder, DatabaseChangeLogProvider.class).addBinding().to(LogDBChangeLogProvider.class);
    }

    @Provides
    @MainDB
    @Singleton
    public DataSource mainDbDataSource(@MainDB DatabaseConfiguration cfg,
                                       MetricRegistry metricRegistry,
                                       Set<DatabaseChangeLogProvider> changeLogProviders) {

        var ds = DataSourceUtils.createDataSource(cfg, "app" /* not called "main" for backward compatibility */, cfg.username(), cfg.password(), metricRegistry);
        if (migrateDb) {
            migrateDb(changeLogProviders, ds, cfg, MainDB.class);
        }
        return ds;
    }

    @Provides
    @LogDB
    @Singleton
    public DataSource logDataSource(@LogDB DatabaseConfiguration cfg,
                                    MetricRegistry metricRegistry,
                                    Set<DatabaseChangeLogProvider> changeLogProviders) {

        var ds = DataSourceUtils.createDataSource(cfg, "log", cfg.username(), cfg.password(), metricRegistry);
        if (migrateDb) {
            migrateDb(changeLogProviders, ds, cfg, LogDB.class);
        }
        return ds;
    }

    @Provides
    @JsonStorageDB
    @Singleton
    public DataSource jsonStorageDbDataSource(@JsonStorageDB DatabaseConfiguration cfg, MetricRegistry metricRegistry) {
        return DataSourceUtils.createDataSource(cfg, "inventory", cfg.username(), cfg.password(), metricRegistry);
    }

    @Provides
    @MainDB
    @Singleton
    public Configuration mainDbJooqConfiguration(@MainDB DataSource ds) {
        return DataSourceUtils.createJooqConfiguration(ds);
    }

    @Provides
    @LogDB
    @Singleton
    public Configuration logDbJooqConfiguration(@LogDB DataSource ds) {
        return DataSourceUtils.createJooqConfiguration(ds);
    }

    @Provides
    @JsonStorageDB
    @Singleton
    public Configuration jsonStorageDbJooqConfiguration(@JsonStorageDB DataSource ds) {
        return DataSourceUtils.createJooqConfiguration(ds);
    }

    private static void migrateDb(Set<DatabaseChangeLogProvider> changeLogProviders,
                                  DataSource ds,
                                  DatabaseConfiguration cfg,
                                  Class<? extends Annotation> annotation) {

        var providers = changeLogProviders.stream()
                // can't inject a set of objects with the same qualifier, filter manually
                .filter(p -> p.getClass().getAnnotation(annotation) != null)
                .sorted(Comparator.comparingInt(DatabaseChangeLogProvider::order))
                .toList();

        if (providers.isEmpty()) {
            // classpath issue or a bug?
            var availableProviders = changeLogProviders.stream().map(DatabaseChangeLogProvider::getChangeLogPath).sorted();
            throw new IllegalStateException("Can't find a DatabaseChangeLogProvider for %s (most likely a bug). Available providers: %s"
                    .formatted(annotation.getName(), availableProviders.collect(Collectors.joining(", "))));
        }

        providers.forEach(p -> DataSourceUtils.migrateDb(ds, p, cfg.changeLogParameters()));
    }
}
