package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.PerformedActionType;

import java.io.Serializable;

public class CreateProjectResponse implements Serializable {

    private final boolean ok = true;
    private final PerformedActionType actionType;

    @JsonCreator
    public CreateProjectResponse(@JsonProperty("actionType") PerformedActionType actionType) {
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
        return "CreateProjectResponse{" +
                "ok=" + ok +
                ", actionType=" + actionType +
                '}';
    }
}
