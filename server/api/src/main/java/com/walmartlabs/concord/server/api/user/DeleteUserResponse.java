package com.walmartlabs.concord.server.api.user;

import java.io.Serializable;

public class DeleteUserResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "DeleteUserResponse{" +
                "ok=" + ok +
                '}';
    }
}
