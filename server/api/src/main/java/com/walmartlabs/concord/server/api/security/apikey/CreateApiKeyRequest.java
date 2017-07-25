package com.walmartlabs.concord.server.api.security.apikey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.UUID;

public class CreateApiKeyRequest implements Serializable {

    @NotNull
    private final UUID userId;

    @JsonCreator
    public CreateApiKeyRequest(@JsonProperty("userId") UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public String toString() {
        return "CreateApiKeyRequest{" +
                "userId=" + userId +
                '}';
    }
}
