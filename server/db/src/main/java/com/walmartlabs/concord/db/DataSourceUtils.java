package com.walmartlabs.concord.db;

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

import com.codahale.metrics.MetricRegistry;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.ui.LoggerUIService;
import org.jooq.Configuration;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameStyle;
import org.jooq.conf.Settings;
import org.jooq.impl.DefaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

public final class DataSourceUtils {

    private static final Logger log = LoggerFactory.getLogger(DataSourceUtils.class);

    private static final int MIGRATION_MAX_RETRIES = 10;
    private static final int MIGRATION_RETRY_DELAY = 10000;

    public static DataSource createDataSource(DatabaseConfiguration cfg,
                                              String poolName,
                                              String username,
                                              String password,
                                              MetricRegistry metricRegistry) {

        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName(poolName);
        ds.setJdbcUrl(cfg.url());
        ds.setDriverClassName(cfg.driverClassName());
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setAutoCommit(false);
        ds.setMaxLifetime(cfg.maxLifetime().toMillis());
        ds.setMinimumIdle(1);
        ds.setMaximumPoolSize(cfg.maxPoolSize());
        ds.setLeakDetectionThreshold(30000);
        ds.setMetricRegistry(metricRegistry);
        return ds;
    }

    /**
     * Migrate a database using the provided changelog and Liquibase parameters.
     *
     * @param dataSource        datasource to use for migration
     * @param changeLogProvider provider of the changelog
     * @param changeLogParams   Liquibase parameters to use during the migration
     */
    public static void migrateDb(DataSource dataSource,
                                 DatabaseChangeLogProvider changeLogProvider,
                                 Map<String, Object> changeLogParams) {

        int retries = MIGRATION_MAX_RETRIES;
        for (int i = 0; i < retries; i++) {
            try (Connection c = dataSource.getConnection()) {
                log.info("migrateDb -> performing '{}' migration...", changeLogProvider);
                String logPath = changeLogProvider.getChangeLogPath();
                String logTable = changeLogProvider.getChangeLogTable();
                String lockTable = changeLogProvider.getLockTable();
                migrateDb(c, logPath, logTable, lockTable, changeLogParams);
                log.info("migrateDb -> completed '{}' migration..", changeLogProvider);
                break;
            } catch (Exception e) {
                if (i + 1 >= retries) {
                    log.error("migrateDb -> db migration error, giving up", e);
                    throw new RuntimeException(e);
                }

                log.warn("migrateDb -> db migration error, retrying in {}ms: {}", MIGRATION_RETRY_DELAY, e.getMessage());
                try {
                    Thread.sleep(MIGRATION_RETRY_DELAY);
                } catch (InterruptedException ee) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public static Configuration createJooqConfiguration(DataSource ds) {
        Settings settings = new Settings();
        settings.setRenderSchema(false);
        settings.setRenderCatalog(false);
        settings.setRenderNameStyle(RenderNameStyle.AS_IS);
        return new DefaultConfiguration()
                .set(settings)
                .set(ds)
                .set(SQLDialect.POSTGRES);
    }

    private static void migrateDb(Connection conn,
                                  String logPath,
                                  String logTable,
                                  String lockTable,
                                  Map<String, Object> params) throws Exception {

        Database db = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(conn));

        db.setDatabaseChangeLogTableName(logTable);
        db.setDatabaseChangeLogLockTableName(lockTable);

        Liquibase lb = new Liquibase(logPath, new ClassLoaderResourceAccessor(), db);

        if (params != null) {
            params.forEach(lb::setChangeLogParameter);
        }

        Scope.enter(Map.of(Scope.Attr.ui.name(), new LoggerUIService()));
        lb.update((String) null);
    }

    private DataSourceUtils() {
    }
}
