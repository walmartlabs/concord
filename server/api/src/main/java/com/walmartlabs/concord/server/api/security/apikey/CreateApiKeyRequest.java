package com.walmartlabs.concord.server.api.security.apikey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

public class CreateApiKeyRequest implements Serializable {

    private final UUID userId;

    private final String username;

    @JsonCreator
    public CreateApiKeyRequest(@JsonProperty("userId") UUID userId,
                               @JsonProperty("username") String username) {
        this.userId = userId;
        this.username = username;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return "CreateApiKeyRequest{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                '}';
    }
}
