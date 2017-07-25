package com.walmartlabs.concord.server.api.security.apikey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

public class CreateApiKeyResponse implements Serializable {

    private final boolean ok = true;
    private final UUID id;
    private final String key;

    @JsonCreator
    public CreateApiKeyResponse(@JsonProperty("id") UUID id, @JsonProperty("key") String key) {
        this.id = id;
        this.key = key;
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "CreateApiKeyResponse{" +
                "ok=" + ok +
                ", id=" + id +
                ", key='" + key + '\'' +
                '}';
    }
}
