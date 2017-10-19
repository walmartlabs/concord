package com.walmartlabs.concord.server.api.inventory;

import java.io.Serializable;

public class DeleteInventoryResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "DeleteInventoryResponse{" +
                "ok=" + ok +
                '}';
    }
}
