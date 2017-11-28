package com.walmartlabs.concord.server.api.org;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.OperationResult;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class CreateOrganizationResponse implements Serializable {

    private final boolean ok = true;
    private final UUID id;
    private final OperationResult result;

    @JsonCreator
    public CreateOrganizationResponse(@JsonProperty("id") UUID id,
                                      @JsonProperty("result") OperationResult result) {
        this.id = id;
        this.result = result;
    }

    public boolean isOk() {
        return ok;
    }

    public UUID getId() {
        return id;
    }

    public OperationResult getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "CreateOrganizationResponse{" +
                "ok=" + ok +
                ", id=" + id +
                ", result=" + result +
                '}';
    }
}
