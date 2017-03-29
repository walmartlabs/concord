package com.walmartlabs.concord.server.api.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

@JsonInclude(Include.NON_NULL)
public class CreateRoleResponse implements Serializable {

    private final boolean ok = true;
    private final boolean created;

    @JsonCreator
    public CreateRoleResponse(@JsonProperty("created") boolean created) {
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
        return "CreateRoleResponse{" +
                "ok=" + ok +
                ", created=" + created +
                '}';
    }
}
