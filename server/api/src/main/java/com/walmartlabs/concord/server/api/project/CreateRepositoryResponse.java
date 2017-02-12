package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class CreateRepositoryResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "CreateRepositoryResponse{" +
                "ok=" + ok +
                '}';
    }
}
