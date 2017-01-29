package com.walmartlabs.concord.server.api.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class CreateUserResponse implements Serializable {

    private final boolean ok = true;
    private final String id;

    @JsonCreator
    public CreateUserResponse(@JsonProperty("id") String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }


    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "CreateUserResponse{" +
                "ok=" + ok +
                ", id='" + id + '\'' +
                '}';
    }
}
