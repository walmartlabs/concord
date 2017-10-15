package com.walmartlabs.concord.server.api.process;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class StartProcessResponse implements Serializable {

    private final boolean ok = true;
    private final UUID instanceId;
    private final Map<String, Object> out;

    @JsonCreator
    public StartProcessResponse(@JsonProperty("instanceId") UUID instanceId,
                                @JsonProperty("out") Map<String, Object> out) {

        this.instanceId = instanceId;
        this.out = out;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public Map<String, Object> getOut() {
        return out;
    }

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "StartProcessResponse{" +
                "ok=" + ok +
                ", instanceId=" + instanceId +
                ", out=" + out +
                '}';
    }
}
