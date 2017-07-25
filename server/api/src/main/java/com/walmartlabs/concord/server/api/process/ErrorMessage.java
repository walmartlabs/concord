package com.walmartlabs.concord.server.api.process;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class ErrorMessage implements Serializable {

    private final UUID instanceId;
    private final String message;
    private final String details;
    private final String stacktrace;

    @JsonCreator
    public ErrorMessage(@JsonProperty("instanceId") UUID instanceId,
                        @JsonProperty("message") String message,
                        @JsonProperty("details") String details,
                        @JsonProperty("stacktrace") String stacktrace) {

        this.instanceId = instanceId;
        this.message = message;
        this.details = details;
        this.stacktrace = stacktrace;
    }

    public UUID getInstanceId() {
        return instanceId;
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

    @Override
    public String toString() {
        return "ErrorMessage{" +
                "instanceId=" + instanceId +
                ", message='" + message + '\'' +
                ", details='" + details + '\'' +
                ", stacktrace='" + stacktrace + '\'' +
                '}';
    }
}
