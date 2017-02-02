package com.walmartlabs.concord.common.db;

import com.google.inject.AbstractModule;
import org.jooq.Configuration;

import javax.sql.DataSource;

public class DatabaseModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DataSource.class).toProvider(DataSourceProvider.class);
        bind(Configuration.class).toProvider(JooqConfigurationProvider.class);
    }
}
