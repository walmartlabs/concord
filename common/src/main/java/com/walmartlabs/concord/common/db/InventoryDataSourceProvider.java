package com.walmartlabs.concord.common.db;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

@Singleton
@Named("inventory")
public class InventoryDataSourceProvider implements Provider<DataSource> {

    private static final Logger log = LoggerFactory.getLogger(InventoryDataSourceProvider.class);

    private final DatabaseConfiguration cfg;

    @Inject
    public InventoryDataSourceProvider(@Named("inventory") DatabaseConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public DataSource get() {
        log.info("get -> creating a new inventory datasource...");

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

        return ds;
    }
}
