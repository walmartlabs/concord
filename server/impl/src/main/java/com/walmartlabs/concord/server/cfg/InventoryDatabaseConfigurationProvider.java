package com.walmartlabs.concord.server.cfg;

import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Named("inventory")
public class InventoryDatabaseConfigurationProvider extends AbstractDatabaseConfigurationProvider {

    public static final String DB_USERNAME_KEY = "DB_INVENTORY_USERNAME";
    public static final String DEFAULT_DB_USERNAME = "postgres";

    public static final String DB_PASSWORD_KEY = "DB_INVENTORY_PASSWORD";
    public static final String DEFAULT_DB_PASSWORD = "q1";

    public InventoryDatabaseConfigurationProvider() {
        super(DB_USERNAME_KEY, DEFAULT_DB_USERNAME, DB_PASSWORD_KEY, DEFAULT_DB_PASSWORD);
    }
}
