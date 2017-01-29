package com.walmartlabs.concord.bootstrap.db;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.logging.LogFactory;
import liquibase.logging.LogLevel;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.apache.tomcat.jdbc.pool.PoolProperties;
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

    private static Logger log = LoggerFactory.getLogger(DataSourceProvider.class);

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

        PoolProperties props = new PoolProperties();
        props.setUrl(cfg.getUrl());
        props.setDriverClassName(cfg.getDriverClassName());
        props.setUsername(cfg.getUsername());
        props.setPassword(cfg.getPassword());
        props.setDefaultAutoCommit(false);
        props.setMinIdle(1);
        props.setMaxIdle(5);
        props.setMaxActive(10);

        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        ds.setPoolProperties(props);

        for (DatabaseChangeLogProvider p : changeLogs) {
            try (Connection c = ds.getConnection()) {
                log.info("get -> performing DB migration using '{}' change log...", p);
                migrateDb(c, p.getChangeLogPath(), p.getChangeLogTable(), p.getLockTable());
                log.info("get -> '{}' done", p);
            } catch (Exception e) {
                throw new RuntimeException(e);
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
