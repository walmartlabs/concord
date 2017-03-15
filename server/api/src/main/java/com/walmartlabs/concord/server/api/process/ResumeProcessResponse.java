package com.walmartlabs.concord.server.api.process;

import java.io.Serializable;

public class ResumeProcessResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "ResumeProcessResponse{" +
                "ok=" + ok +
                '}';
    }
}
