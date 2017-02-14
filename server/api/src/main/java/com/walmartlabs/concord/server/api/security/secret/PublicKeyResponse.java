package com.walmartlabs.concord.server.api.security.secret;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

public class PublicKeyResponse implements Serializable {

    private final boolean ok = true;

    @NotNull
    @ConcordKey
    private final String name;

    @NotNull
    private final String publicKey;

    @JsonCreator
    public PublicKeyResponse(@JsonProperty("name") String name,
                             @JsonProperty("publicKey") String publicKey) {

        this.name = name;
        this.publicKey = publicKey;
    }

    public boolean isOk() {
        return ok;
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
                ", name='" + name + '\'' +
                '}';
    }
}
