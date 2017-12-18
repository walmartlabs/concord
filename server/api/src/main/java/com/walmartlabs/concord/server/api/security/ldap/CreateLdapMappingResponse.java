package com.walmartlabs.concord.server.api.security.ldap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordId;
import com.walmartlabs.concord.server.api.OperationResult;

import java.io.Serializable;
import java.util.UUID;

public class CreateLdapMappingResponse implements Serializable {

    private final boolean ok = true;

    @ConcordId
    private final UUID id;
    private final OperationResult result;

    @JsonCreator
    public CreateLdapMappingResponse(@JsonProperty("id") UUID id,
                                     @JsonProperty("result") OperationResult result) {
        this.id = id;
        this.result = result;
    }

    public UUID getId() {
        return id;
    }

    public boolean isOk() {
        return ok;
    }

    public OperationResult getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "CreateLdapMappingResponse{" +
                "ok=" + ok +
                ", id=" + id +
                ", result=" + result +
                '}';
    }
}
