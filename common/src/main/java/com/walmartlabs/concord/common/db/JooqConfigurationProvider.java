package com.walmartlabs.concord.common.db;

import org.jooq.Configuration;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameStyle;
import org.jooq.impl.DefaultConfiguration;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

@Singleton
public class JooqConfigurationProvider implements Provider<Configuration> {

    private final DataSource dataSource;
    private final SQLDialect dialect;

    @Inject
    public JooqConfigurationProvider(DataSource dataSource, DatabaseConfiguration cfg) {
        this.dataSource = dataSource;
        this.dialect = cfg.getDialect();
    }

    @Override
    public Configuration get() {
        Configuration jooq = new DefaultConfiguration()
                .set(dataSource)
                .set(dialect);

        jooq.settings().setRenderNameStyle(RenderNameStyle.AS_IS); // don't quote object names

        return jooq;
    }
}
