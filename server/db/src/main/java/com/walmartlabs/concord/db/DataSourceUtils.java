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
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Map;

public final class DataSourceUtils {

    private static final Logger log = LoggerFactory.getLogger(DataSourceUtils.class);

    private static final int MIGRATION_MAX_RETRIES = 10;
    private static final Duration MIGRATION_RETRY_DELAY = Duration.ofSeconds(10);
    private static final Duration MAX_LOCK_WAIT_TIME = Duration.ofMinutes(5);
    private static final Duration FAILED_LOCK_ATTEMPT_DELAY = Duration.ofSeconds(3);

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
     * @param cfg               database config
     * @param changeLogProvider provider of the changelog
     */
    public static void migrateDb(DatabaseConfiguration cfg,
                                 DatabaseChangeLogProvider changeLogProvider) {

        try {
            Class.forName(cfg.driverClassName());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize the JDBC driver: " + e.getMessage());
        }

        int retries = MIGRATION_MAX_RETRIES;
        for (int i = 0; i < retries; i++) {
            try (Connection conn = DriverManager.getConnection(cfg.url(), cfg.username(), cfg.password())) {
                String logPath = changeLogProvider.getChangeLogPath();
                String logTable = changeLogProvider.getChangeLogTable();
                String lockTable = changeLogProvider.getLockTable();

                long start = System.currentTimeMillis();
                while (!tryLock(conn, logTable)) {
                    if (System.currentTimeMillis() - start >= MAX_LOCK_WAIT_TIME.toMillis()) {
                        throw new Exception("Timeout to obtain the lock for " + logPath);
                    }

                    log.info("migrateDb -> waiting for the lock for {}", logPath);
                    Thread.sleep(FAILED_LOCK_ATTEMPT_DELAY.toMillis());
                }

                log.info("migrateDb -> performing '{}' migration...", changeLogProvider);
                applyMigrations(conn, logPath, logTable, lockTable, cfg.changeLogParameters());
                log.info("migrateDb -> completed '{}' migration..", changeLogProvider);
                break;
            } catch (Exception e) {
                if (i + 1 >= retries) {
                    log.error("migrateDb -> db migration error, giving up", e);
                    throw new RuntimeException(e);
                }

                log.warn("migrateDb -> db migration error, retrying in {}ms: {}", MIGRATION_RETRY_DELAY, e.getMessage());
                try {
                    Thread.sleep(MIGRATION_RETRY_DELAY.toMillis());
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

    private static void applyMigrations(Connection conn,
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

    private static boolean tryLock(Connection conn, String logTable) throws Exception {
        log.info("tryLock -> trying to take the lock for {}", logTable);
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setInt(1, logTable.hashCode());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean result = rs.getBoolean(1);
                    if (result) {
                        log.info("tryLock -> successfully grabbed the lock for {}", logTable);
                    }
                    return result;
                }
            }
        }
        return false;
    }

    private DataSourceUtils() {
    }
}
