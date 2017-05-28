package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

public class EncryptValueRequest implements Serializable {

    @NotNull
    private final String value;

    @JsonCreator
    public EncryptValueRequest(@JsonProperty("value") String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "EncryptValueRequest{" +
                "value='" + value + '\'' +
                '}';
    }
}
