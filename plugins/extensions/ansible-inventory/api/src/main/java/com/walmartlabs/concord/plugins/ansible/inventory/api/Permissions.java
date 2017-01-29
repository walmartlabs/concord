package com.walmartlabs.concord.plugins.ansible.inventory.api;

public final class Permissions {

    public static final String INVENTORY_CREATE_NEW = "inventory:create";
    public static final String INVENTORY_USE_INSTANCE = "inventory:use:%s";
    public static final String INVENTORY_MANAGE_INSTANCE = "inventory:manage:%s";

    private Permissions() {
    }
}
