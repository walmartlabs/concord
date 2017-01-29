package com.walmartlabs.concord.server.api.security.apikey;

import java.io.Serializable;

public class DeleteApiKeyResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "DeleteApiKeyResponse{" +
                "ok=" + ok +
                '}';
    }
}
