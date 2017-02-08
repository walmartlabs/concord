package com.walmartlabs.concord.server.api.security.secret;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class UploadSecretResponse implements Serializable {

    private final boolean ok = true;
    private final String id;

    @JsonCreator
    public UploadSecretResponse(@JsonProperty("id") String id) {
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
        return "UploadSecretResponse{" +
                "ok=" + ok +
                ", id='" + id + '\'' +
                '}';
    }
}
