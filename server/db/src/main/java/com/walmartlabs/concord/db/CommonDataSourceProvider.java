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
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Set;

@Singleton
public class CommonDataSourceProvider extends AbstractDataSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(CommonDataSourceProvider.class);

    private static final int MIGRATION_MAX_RETRIES = 10;
    private static final int MIGRATION_RETRY_DELAY = 10000;

    private final Set<DatabaseChangeLogProvider> changeLogs;

    @Inject
    public CommonDataSourceProvider(DatabaseConfiguration cfg, Set<DatabaseChangeLogProvider> changeLogs) {
        super(cfg.getUrl(), cfg.getDriverClassName(),
                cfg.getUsername(), cfg.getPassword(),
                cfg.getMaxPoolSize());
        this.changeLogs = changeLogs;
    }

    @Override
    public DataSource get() {
        DataSource ds = super.get();

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
