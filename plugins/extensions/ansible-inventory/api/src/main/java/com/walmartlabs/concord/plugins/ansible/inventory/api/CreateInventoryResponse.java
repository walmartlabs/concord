package com.walmartlabs.concord.plugins.ansible.inventory.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class CreateInventoryResponse implements Serializable {

    private final boolean ok = true;
    private final String id;

    @JsonCreator
    public CreateInventoryResponse(@JsonProperty("id") String id) {
        this.id = id;
    }

    public boolean isOk() {
        return ok;
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "CreateInventoryResponse{" +
                "ok=" + ok +
                ", id='" + id + '\'' +
                '}';
    }
}
