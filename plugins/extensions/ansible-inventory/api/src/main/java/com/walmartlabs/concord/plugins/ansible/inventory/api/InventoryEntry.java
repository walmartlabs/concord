package com.walmartlabs.concord.plugins.ansible.inventory.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class InventoryEntry implements Serializable {

    private final String id;
    private final String name;
    private final boolean readOnly;

    @JsonCreator
    public InventoryEntry(@JsonProperty("id") String id,
                          @JsonProperty("name") String name,
                          @JsonProperty("readOnly") boolean readOnly) {

        this.id = id;
        this.name = name;
        this.readOnly = readOnly;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public String toString() {
        return "InventoryEntry{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", readOnly=" + readOnly +
                '}';
    }
}
