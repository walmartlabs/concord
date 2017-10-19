package com.walmartlabs.concord.server.cfg;

import com.walmartlabs.concord.db.DatabaseConfiguration;
import org.jooq.SQLDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;

public abstract class AbstractDatabaseConfigurationProvider implements Provider<DatabaseConfiguration> {

    private final Logger log;

    public static final String DB_DIALECT_KEY = "DB_DIALECT";
    public static final String DEFAULT_DB_DIALECT = "POSTGRES";

    public static final String DB_DRIVER_KEY = "DB_DRIVER";
    public static final String DEFAULT_DB_DRIVER = "org.postgresql.Driver";

    public static final String DB_URL_KEY = "DB_URL";
    public static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/postgres";

    private final String userNameKey;
    private final String defaultUserName;
    private final String passwordKey;
    private final String defaultPassword;

    public AbstractDatabaseConfigurationProvider(String userNameKey, String defaultUserName,
                                                 String passwordKey, String defaultPassword) {

        this.userNameKey = userNameKey;
        this.defaultUserName = defaultUserName;
        this.passwordKey = passwordKey;
        this.defaultPassword = defaultPassword;

        this.log = LoggerFactory.getLogger(this.getClass());
    }

    @Override
    public DatabaseConfiguration get() {
        String dialect = Utils.getEnv(DB_DIALECT_KEY, DEFAULT_DB_DIALECT).toUpperCase();
        String driverClassName = Utils.getEnv(DB_DRIVER_KEY, DEFAULT_DB_DRIVER);
        String url = Utils.getEnv(DB_URL_KEY, DEFAULT_DB_URL);
        String username = Utils.getEnv(userNameKey, defaultUserName);
        String password = Utils.getEnv(passwordKey, defaultPassword);

        log.info("get -> using: {} - {}@{}", dialect, username, url);
        return new DatabaseConfiguration(SQLDialect.valueOf(dialect), driverClassName, url, username, password);
    }
}
