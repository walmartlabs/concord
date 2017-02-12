package com.walmartlabs.concord.server.api.project;

import java.io.Serializable;

public class UpdateRepositoryResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "UpdateRepositoryResponse{" +
                "ok=" + ok +
                '}';
    }
}
