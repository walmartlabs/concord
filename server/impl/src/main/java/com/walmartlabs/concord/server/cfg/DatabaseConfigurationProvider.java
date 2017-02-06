package com.walmartlabs.concord.server.cfg;

import com.walmartlabs.concord.common.db.DatabaseConfiguration;
import org.jooq.SQLDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

@Named
@Singleton
public class DatabaseConfigurationProvider implements Provider<DatabaseConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfigurationProvider.class);

    public static final String DB_DIALECT_KEY = "DB_DIALECT";
    public static final String DEFAULT_DB_DIALECT = "H2";

    public static final String DB_DRIVER_KEY = "DB_DRIVER";
    public static final String DEFAULT_DB_DRIVER = "org.h2.Driver";

    public static final String DB_URL_KEY = "DB_URL";
    public static final String DEFAULT_DB_URL = "jdbc:h2:mem:test";

    public static final String DB_USERNAME_KEY = "DB_USERNAME";
    public static final String DEFAULT_DB_USERNAME = "sa";

    public static final String DB_PASSWORD_KEY = "DB_PASSWORD";
    public static final String DEFAULT_DB_PASSWORD = null;

    @Override
    public DatabaseConfiguration get() {
        String dialect = Utils.getEnv(DB_DIALECT_KEY, DEFAULT_DB_DIALECT).toUpperCase();
        String driverClassName = Utils.getEnv(DB_DRIVER_KEY, DEFAULT_DB_DRIVER);
        String url = Utils.getEnv(DB_URL_KEY, DEFAULT_DB_URL);
        String username = Utils.getEnv(DB_USERNAME_KEY, DEFAULT_DB_USERNAME);
        String password = Utils.getEnv(DB_PASSWORD_KEY, DEFAULT_DB_PASSWORD);

        log.info("get -> using: {} - {}@{}", dialect, username, url);
        return new DatabaseConfiguration(SQLDialect.valueOf(dialect), driverClassName, url, username, password);
    }
}
