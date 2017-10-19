package com.walmartlabs.concord.server;

import com.google.inject.AbstractModule;
import com.walmartlabs.concord.db.CommonDataSourceProvider;
import com.walmartlabs.concord.db.JooqConfigurationProvider;
import org.jooq.Configuration;

import javax.sql.DataSource;

public class DatabaseModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DataSource.class).toProvider(CommonDataSourceProvider.class);
        bind(Configuration.class).toProvider(JooqConfigurationProvider.class);
    }
}
