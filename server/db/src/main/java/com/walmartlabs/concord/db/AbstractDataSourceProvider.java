package com.walmartlabs.concord.db;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import javax.sql.DataSource;

public abstract class AbstractDataSourceProvider implements Provider<DataSource> {

    private static final Logger log = LoggerFactory.getLogger(AbstractDataSourceProvider.class);

    private final String url;
    private final String driverClassName;
    private final String username;
    private final String password;

    public AbstractDataSourceProvider(String url, String driverClassName, String username, String password) {
        this.url = url;
        this.driverClassName = driverClassName;
        this.username = username;
        this.password = password;
    }

    @Override
    public DataSource get() {
        log.info("get -> creating a new datasource...");

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setDriverClassName(driverClassName);
        ds.setUsername(username);
        ds.setPassword(password);

        ds.setAutoCommit(false);
        ds.setMaxLifetime(Long.MAX_VALUE);

        ds.setMinimumIdle(1);
        ds.setMaximumPoolSize(10);

        ds.setLeakDetectionThreshold(10000);

        return ds;
    }
}
