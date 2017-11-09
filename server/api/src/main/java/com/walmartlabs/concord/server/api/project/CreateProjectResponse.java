package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.PerformedActionType;

import java.io.Serializable;
import java.util.UUID;

public class CreateProjectResponse implements Serializable {

    private final boolean ok = true;
    private final UUID id;
    private final PerformedActionType actionType;

    @JsonCreator
    public CreateProjectResponse(@JsonProperty("id") UUID id,
                                 @JsonProperty("actionType") PerformedActionType actionType) {
        this.id = id;
        this.actionType = actionType;
    }

    public boolean isOk() {
        return ok;
    }

    public UUID getId() {
        return id;
    }

    public PerformedActionType getActionType() {
        return actionType;
    }

    @Override
    public String toString() {
        return "CreateProjectResponse{" +
                "ok=" + ok +
                ", id=" + id +
                ", actionType=" + actionType +
                '}';
    }
}
