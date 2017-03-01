package com.walmartlabs.concord.server.api.process;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class ErrorMessage implements Serializable {

    private final String message;
    private final String details;

    @JsonCreator
    public ErrorMessage(@JsonProperty("message") String message, @JsonProperty("details") String details) {
        this.message = message;
        this.details = details;
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }
}
