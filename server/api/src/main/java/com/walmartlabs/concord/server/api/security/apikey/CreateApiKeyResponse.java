package com.walmartlabs.concord.server.api.security.apikey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class CreateApiKeyResponse implements Serializable {

    private final boolean ok = true;
    private final String key;

    @JsonCreator
    public CreateApiKeyResponse(@JsonProperty("key") String key) {
        this.key = key;
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
                ", key='" + key + '\'' +
                '}';
    }
}
