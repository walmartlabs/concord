package com.walmartlabs.concord.plugins.ansible.inventory;

public class InventoryException extends Exception {

    public InventoryException(String message) {
        super(message);
    }

    public InventoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
