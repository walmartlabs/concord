package com.walmartlabs.concord.server.org.inventory;

import java.io.Serializable;

public class InventoryDataItem implements Serializable {

    private final String path;

    private final int level;

    private final Object data;

    public InventoryDataItem(String path, int level, Object data) {
        this.path = path;
        this.level = level;
        this.data = data;
    }

    public String getPath() {
        return path;
    }

    public Object getData() {
        return data;
    }

    public int getLevel() {
        return level;
    }
}
