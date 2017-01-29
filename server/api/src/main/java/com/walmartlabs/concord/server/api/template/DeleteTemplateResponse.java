package com.walmartlabs.concord.server.api.template;

import java.io.Serializable;

public class DeleteTemplateResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "DeleteTemplateResponse{" +
                "ok=" + ok +
                '}';
    }
}
