package com.walmartlabs.concord.server.api.org.landing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.OperationResult;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class CreateLandingResponse implements Serializable {

    private final boolean ok = true;
    private final OperationResult result;
    private final UUID id;

    @JsonCreator
    public CreateLandingResponse(@JsonProperty("result") OperationResult result,
                                 @JsonProperty("id") UUID id) {

        this.result = result;
        this.id = id;
    }

    public boolean isOk() {
        return ok;
    }

    public OperationResult getResult() {
        return result;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public String toString() {
        return "CreateInventoryResponse{" +
                "result=" + result +
                ", id=" + id +
                '}';
    }
}
