package com.walmartlabs.concord.server.api.org.inventory;

import java.io.Serializable;

public class DeleteInventoryQueryResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "DeleteInventoryQueryResponse{" +
                "ok=" + ok +
                '}';
    }
}
