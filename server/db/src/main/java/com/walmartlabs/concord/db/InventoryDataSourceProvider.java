package com.walmartlabs.concord.db;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Named("inventory")
public class InventoryDataSourceProvider extends AbstractDataSourceProvider {

    @Inject
    public InventoryDataSourceProvider(@Named("inventory") DatabaseConfiguration cfg) {
        super(cfg.getUrl(), cfg.getDriverClassName(), cfg.getUsername(), cfg.getPassword());
    }
}
