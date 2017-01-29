package com.walmartlabs.concord.server.api.process;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class StartProcessResponse implements Serializable {

    private final boolean ok = true;
    private final String instanceId;

    @JsonCreator
    public StartProcessResponse(@JsonProperty("instanceId") String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "StartProcessResponse{" +
                "ok=" + ok +
                ", instanceId='" + instanceId + '\'' +
                '}';
    }
}
