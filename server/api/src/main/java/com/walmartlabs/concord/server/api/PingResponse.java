package com.walmartlabs.concord.server.api;

import java.io.Serializable;

public class PingResponse implements Serializable {

    private final boolean ok;

    public PingResponse(boolean ok) {
        this.ok = ok;
    }

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "PingResponse{" +
                "ok=" + ok +
                '}';
    }
}
