package com.walmartlabs.concord.server.api.security.secret;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@JsonInclude(Include.NON_NULL)
public class PublicKeyResponse implements Serializable {

    private final boolean ok = true;

    @NotNull
    @ConcordKey
    private final String name;

    @NotNull
    private final String publicKey;

    private final String exportPassword;

    @JsonCreator
    public PublicKeyResponse(@JsonProperty("name") String name,
                             @JsonProperty("publicKey") String publicKey,
                             @JsonProperty("exportPassword") String exportPassword) {

        this.name = name;
        this.publicKey = publicKey;
        this.exportPassword = exportPassword;
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

    public String getExportPassword() {
        return exportPassword;
    }

    @Override
    public String toString() {
        return "PublicKeyResponse{" +
                "ok=" + ok +
                ", name='" + name + '\'' +
                '}';
    }
}
