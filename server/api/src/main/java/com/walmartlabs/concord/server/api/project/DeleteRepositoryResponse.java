package com.walmartlabs.concord.server.api.project;

import java.io.Serializable;

public class DeleteRepositoryResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "DeleteRepositoryResponse{" +
                "ok=" + ok +
                '}';
    }
}
