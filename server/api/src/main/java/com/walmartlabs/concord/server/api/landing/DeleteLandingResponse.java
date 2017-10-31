package com.walmartlabs.concord.server.api.landing;

import java.io.Serializable;

public class DeleteLandingResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "DeleteLandingResponse{" +
                "ok=" + ok +
                '}';
    }
}
