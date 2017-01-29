package com.walmartlabs.concord.plugins.ansible.inventory.api;

import java.io.Serializable;

public class UpdateInventoryResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "UpdateInventoryResponse{" +
                "ok=" + ok +
                '}';
    }
}
