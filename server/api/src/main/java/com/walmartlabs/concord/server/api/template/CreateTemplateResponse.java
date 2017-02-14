package com.walmartlabs.concord.server.api.template;

import java.io.Serializable;

public class CreateTemplateResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "CreateTemplateResponse{" +
                "ok=" + ok +
                '}';
    }
}
