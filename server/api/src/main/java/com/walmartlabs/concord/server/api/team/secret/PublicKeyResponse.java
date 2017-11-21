package com.walmartlabs.concord.server.api.team.secret;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.OperationResult;

import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class PublicKeyResponse extends SecretOperationResponse {

    private final String publicKey;

    @JsonCreator
    public PublicKeyResponse(@JsonProperty("id") UUID id,
                             @JsonProperty("result") OperationResult result,
                             @JsonProperty("password") String password,
                             @JsonProperty("publicKey") String publicKey) {

        super(id, result, password);
        this.publicKey = publicKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    @Override
    public String toString() {
        return "PublicKeyResponse{" +
                "publicKey='" + publicKey + '\'' +
                "} " + super.toString();
    }
}
