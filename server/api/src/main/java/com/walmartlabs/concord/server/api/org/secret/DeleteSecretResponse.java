package com.walmartlabs.concord.server.api.org.secret;

import java.io.Serializable;

public class DeleteSecretResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "DeleteSecretResponse{" +
                "ok=" + ok +
                '}';
    }
}
