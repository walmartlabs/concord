package com.walmartlabs.concord.server.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class GenericOperationResultResponse implements Serializable {

    private final boolean ok = true;
    private final OperationResult result;

    @JsonCreator
    public GenericOperationResultResponse(@JsonProperty("result") OperationResult result) {
        this.result = result;
    }

    public OperationResult getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "GenericOperationResultResponse{" +
                "ok=" + ok +
                ", result=" + result +
                '}';
    }
}
