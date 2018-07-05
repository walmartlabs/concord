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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.logging.LogFactory;
import liquibase.logging.LogLevel;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jooq.Configuration;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameStyle;
import org.jooq.conf.Settings;
import org.jooq.impl.DefaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;

public class DatabaseModule extends AbstractModule {

    private static final Logger log = LoggerFactory.getLogger(DatabaseModule.class);

    private static final int MIGRATION_MAX_RETRIES = 10;
    private static final int MIGRATION_RETRY_DELAY = 10000;

    private static final String DB_CHANGELOG_PATH = "com/walmartlabs/concord/server/db/liquibase.xml";
    private static final String DB_CHANGELOG_LOG_TABLE = "SERVER_DB_LOG";
    private static final String DB_CHANGELOG_LOCK_TABLE = "SERVER_DB_LOCK";

    @Override
    protected void configure() {
    }

    @Provides
    @Named("app")
    @Singleton
    public DataSource appDataSource(DatabaseConfiguration cfg) {
        DataSource ds = createDataSource(cfg, cfg.getAppUsername(), cfg.getAppPassword());

        int retries = MIGRATION_MAX_RETRIES;
        for (int i = 0; i < retries; i++) {
            try (Connection c = ds.getConnection()) {
                log.info("get -> performing DB migration...");
                migrateDb(c, DB_CHANGELOG_PATH, DB_CHANGELOG_LOG_TABLE, DB_CHANGELOG_LOCK_TABLE);
                log.info("get -> done");
                break;
            } catch (Exception e) {
                if (i + 1 >= retries) {
                    log.error("get -> db migration error, giving up", e);
                    throw new RuntimeException(e);
                }

                log.warn("get -> db migration error, retrying in {}ms: {}", MIGRATION_RETRY_DELAY, e.getMessage());
                try {
                    Thread.sleep(MIGRATION_RETRY_DELAY);
                } catch (InterruptedException ee) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        return ds;
    }

    @Provides
    @Named("inventory")
    @Singleton
    public DataSource inventoryDataSource(DatabaseConfiguration cfg) {
        return createDataSource(cfg, cfg.getInventoryUsername(), cfg.getInventoryPassword());
    }

    @Provides
    @Singleton
    public Configuration appJooqConfiguration(@Named("app") DataSource ds) {
        return createJooqConfiguration(ds);
    }

    @Provides
    @Named("inventory")
    @Singleton
    public Configuration inventoryJooqConfiguration(@Named("inventory") DataSource ds) {
        return createJooqConfiguration(ds);
    }

    private static DataSource createDataSource(DatabaseConfiguration cfg, String username, String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(cfg.getUrl());
        ds.setDriverClassName(cfg.getDriverClassName());
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setAutoCommit(false);
        ds.setMaxLifetime(Long.MAX_VALUE);
        ds.setMinimumIdle(1);
        ds.setMaximumPoolSize(cfg.getMaxPoolSize());
        ds.setLeakDetectionThreshold(10000);
        return ds;
    }

    private static void migrateDb(Connection conn, String logPath, String logTable, String lockTable) throws Exception {
        LogFactory.getInstance().setDefaultLoggingLevel(LogLevel.WARNING);

        Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
        db.setDatabaseChangeLogTableName(logTable);
        db.setDatabaseChangeLogLockTableName(lockTable);

        Liquibase lb = new Liquibase(logPath, new ClassLoaderResourceAccessor(), db);
        lb.update((String) null);
    }

    private static Configuration createJooqConfiguration(DataSource ds) {
        Settings settings = new Settings();
        settings.setRenderSchema(false);
        settings.setRenderCatalog(false);
        settings.setRenderNameStyle(RenderNameStyle.AS_IS);
        return new DefaultConfiguration()
                .set(settings)
                .set(ds)
                .set(SQLDialect.POSTGRES);
    }
}
