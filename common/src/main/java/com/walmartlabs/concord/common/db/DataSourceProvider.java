package com.walmartlabs.concord.common.db;

import com.zaxxer.hikari.HikariDataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.logging.LogFactory;
import liquibase.logging.LogLevel;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Set;

@Singleton
public class DataSourceProvider implements Provider<DataSource> {

    private static final Logger log = LoggerFactory.getLogger(DataSourceProvider.class);
    private static final int MIGRATION_MAX_RETRIES = 10;
    private static final int MIGRATION_RETRY_DELAY = 10000;

    private final DatabaseConfiguration cfg;
    private final Set<DatabaseChangeLogProvider> changeLogs;

    @Inject
    public DataSourceProvider(DatabaseConfiguration cfg, Set<DatabaseChangeLogProvider> changeLogs) {
        this.cfg = cfg;
        this.changeLogs = changeLogs;
    }

    @Override
    public DataSource get() {
        log.info("get -> creating a new datasource...");

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(cfg.getUrl());
        ds.setDriverClassName(cfg.getDriverClassName());
        ds.setUsername(cfg.getUsername());
        ds.setPassword(cfg.getPassword());

        ds.setAutoCommit(false);
        ds.setMaxLifetime(Long.MAX_VALUE);

        ds.setMinimumIdle(1);
        ds.setMaximumPoolSize(10);

        ds.setLeakDetectionThreshold(10000);

        for (DatabaseChangeLogProvider p : changeLogs) {
            int retries = MIGRATION_MAX_RETRIES;

            for (int i = 0; i < retries; i++) {
                try (Connection c = ds.getConnection()) {
                    log.info("get -> performing DB migration using '{}' change log...", p);
                    migrateDb(c, p.getChangeLogPath(), p.getChangeLogTable(), p.getLockTable());
                    log.info("get -> '{}' done", p);
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
        }

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
}
