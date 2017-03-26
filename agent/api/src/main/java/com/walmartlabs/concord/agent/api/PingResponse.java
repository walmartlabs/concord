package com.walmartlabs.concord.agent.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class PingResponse implements Serializable {

    private final boolean ok;

    @JsonCreator
    public PingResponse(@JsonProperty("ok") boolean ok) {
        this.ok = ok;
    }

    public boolean isOk() {
        return ok;
    }
}
