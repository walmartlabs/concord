package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class CreateProjectResponse implements Serializable {

    private final boolean ok = true;
    private final boolean created;

    @JsonCreator
    public CreateProjectResponse(@JsonProperty("created") boolean created) {
        this.created = created;
    }

    public boolean isOk() {
        return ok;
    }

    public boolean isCreated() {
        return created;
    }

    @Override
    public String toString() {
        return "CreateProjectResponse{" +
                "ok=" + ok +
                ", created=" + created +
                '}';
    }
}
