package com.walmartlabs.concord.server.api.security.secret;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;

@JsonInclude(Include.NON_NULL)
public class PublicKeyResponse extends UploadSecretResponse {

    @NotNull
    @ConcordKey
    private final String name;

    @NotNull
    private final String publicKey;

    @JsonCreator
    public PublicKeyResponse(@JsonProperty("name") String name,
                             @JsonProperty("publicKey") String publicKey,
                             @JsonProperty("exportPassword") String exportPassword) {

        super(exportPassword);
        this.name = name;
        this.publicKey = publicKey;
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
                "ok=" + isOk() +
                ", name='" + name + '\'' +
                '}';
    }
}
