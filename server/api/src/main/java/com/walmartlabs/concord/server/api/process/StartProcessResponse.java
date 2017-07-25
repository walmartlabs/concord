package com.walmartlabs.concord.server.api.process;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

public class StartProcessResponse implements Serializable {

    private final boolean ok = true;
    private final UUID instanceId;

    @JsonCreator
    public StartProcessResponse(@JsonProperty("instanceId") UUID instanceId) {
        this.instanceId = instanceId;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "StartProcessResponse{" +
                "ok=" + ok +
                ", instanceId=" + instanceId +
                '}';
    }
}
