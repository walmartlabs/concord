package com.walmartlabs.concord.server.api.security.secret;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

@JsonInclude(Include.NON_NULL)
public class UploadSecretResponse implements Serializable {

    private final boolean ok = true;
    private final String exportPassword;

    public UploadSecretResponse() {
        this(null);
    }

    @JsonCreator
    public UploadSecretResponse(@JsonProperty("exportPassword") String exportPassword) {
        this.exportPassword = exportPassword;
    }

    public boolean isOk() {
        return ok;
    }

    public String getExportPassword() {
        return exportPassword;
    }

    @Override
    public String toString() {
        return "UploadSecretResponse{" +
                "ok=" + ok +
                '}';
    }
}
