package com.walmartlabs.concord.server.api.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.PerformedActionType;

import java.io.Serializable;

@JsonInclude(Include.NON_NULL)
public class CreateRoleResponse implements Serializable {

    private final boolean ok = true;
    private final PerformedActionType actionType;

    @JsonCreator
    public CreateRoleResponse(@JsonProperty("actionType") PerformedActionType actionType) {
        this.actionType = actionType;
    }

    public boolean isOk() {
        return ok;
    }

    public PerformedActionType getActionType() {
        return actionType;
    }

    @Override
    public String toString() {
        return "CreateRoleResponse{" +
                "ok=" + ok +
                ", actionType=" + actionType +
                '}';
    }
}
