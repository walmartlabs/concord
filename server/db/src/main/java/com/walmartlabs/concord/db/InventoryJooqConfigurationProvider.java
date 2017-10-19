package com.walmartlabs.concord.db;

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
