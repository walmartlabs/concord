package com.walmartlabs.concord.server.api.security.apikey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

public class CreateApiKeyRequest implements Serializable {

    @NotNull
    private final String userId;

    @JsonCreator
    public CreateApiKeyRequest(@JsonProperty("userId") String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public String toString() {
        return "CreateApiKeyRequest{" +
                "userId='" + userId + '\'' +
                '}';
    }
}
