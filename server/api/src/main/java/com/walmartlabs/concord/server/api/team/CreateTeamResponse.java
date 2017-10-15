package com.walmartlabs.concord.server.api.team;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.PerformedActionType;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class CreateTeamResponse implements Serializable {

    private final boolean ok = true;
    private final OperationResult result;
    private final UUID id;

    @JsonCreator
    public CreateTeamResponse(@JsonProperty("result") OperationResult result,
                              @JsonProperty("id") UUID id) {

        this.result = result;
        this.id = id;
    }

    public OperationResult getResult() {
        return result;
    }

    public UUID getId() {
        return id;
    }

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "CreateTeamResponse{" +
                "ok=" + ok +
                ", result=" + result +
                ", id=" + id +
                '}';
    }
}
