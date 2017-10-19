package com.walmartlabs.concord.common.db;

import com.walmartlabs.concord.common.db.DatabaseConfiguration;
import com.walmartlabs.concord.common.db.JooqConfigurationProvider;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.sql.DataSource;

@Singleton
@Named("inventory")
public class InventoryJooqConfigurationProvider extends JooqConfigurationProvider {

    @Inject
    public InventoryJooqConfigurationProvider(@Named("inventory") DataSource dataSource, DatabaseConfiguration cfg) {
        super(dataSource, cfg);
    }
}
