package com.walmartlabs.concord.server.api.process;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

@JsonInclude(Include.NON_NULL)
public class ErrorMessage implements Serializable {

    private final String message;
    private final String details;
    private final String stacktrace;

    @JsonCreator
    public ErrorMessage(@JsonProperty("message") String message,
                        @JsonProperty("details") String details,
                        @JsonProperty("stacktrace") String stacktrace) {

        this.message = message;
        this.details = details;
        this.stacktrace = stacktrace;
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    public String getStacktrace() {
        return stacktrace;
    }
}
