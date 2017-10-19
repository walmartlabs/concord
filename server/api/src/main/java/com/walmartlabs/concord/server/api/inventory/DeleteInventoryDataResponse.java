package com.walmartlabs.concord.server.api.inventory;

import java.io.Serializable;

public class DeleteInventoryDataResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "DeleteInventoryDataResponse{" +
                "ok=" + ok +
                '}';
    }
}
