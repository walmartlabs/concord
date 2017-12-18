package com.walmartlabs.concord.server.api.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.OperationResult;

import java.io.Serializable;

@JsonInclude(Include.NON_NULL)
public class CreateRoleResponse implements Serializable {

    private final boolean ok = true;
    private final OperationResult result;

    @JsonCreator
    public CreateRoleResponse(@JsonProperty("result") OperationResult result) {
        this.result = result;
    }

    public boolean isOk() {
        return ok;
    }

    public OperationResult getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "CreateRoleResponse{" +
                "ok=" + ok +
                ", result=" + result +
                '}';
    }
}
