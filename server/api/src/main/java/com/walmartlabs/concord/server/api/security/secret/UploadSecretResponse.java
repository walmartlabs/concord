package com.walmartlabs.concord.server.api.security.secret;

import java.io.Serializable;

public class UploadSecretResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "UploadSecretResponse{" +
                "ok=" + ok +
                '}';
    }
}
