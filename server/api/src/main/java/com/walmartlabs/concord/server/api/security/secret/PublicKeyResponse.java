package com.walmartlabs.concord.server.api.security.secret;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class PublicKeyResponse implements Serializable {

    private final boolean ok = true;
    private final String id;
    private final String name;
    private final String publicKey;

    @JsonCreator
    public PublicKeyResponse(@JsonProperty("id") String id, @JsonProperty("name") String name, @JsonProperty("publicKey") String publicKey) {
        this.id = id;
        this.name = name;
        this.publicKey = publicKey;
    }

    public boolean isOk() {
        return ok;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPublicKey() {
        return publicKey;
    }

    @Override
    public String toString() {
        return "PublicKeyResponse{" +
                "ok=" + ok +
                ", id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
