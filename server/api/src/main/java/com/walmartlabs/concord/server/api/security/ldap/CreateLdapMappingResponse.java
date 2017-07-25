package com.walmartlabs.concord.server.api.security.ldap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordId;
import com.walmartlabs.concord.server.api.PerformedActionType;

import java.io.Serializable;
import java.util.UUID;

public class CreateLdapMappingResponse implements Serializable {

    private final boolean ok = true;

    @ConcordId
    private final UUID id;
    private final PerformedActionType actionType;

    @JsonCreator
    public CreateLdapMappingResponse(@JsonProperty("id") UUID id,
                                     @JsonProperty("actionType") PerformedActionType actionType) {
        this.id = id;
        this.actionType = actionType;
    }

    public UUID getId() {
        return id;
    }

    public boolean isOk() {
        return ok;
    }

    public PerformedActionType getActionType() {
        return actionType;
    }

    @Override
    public String toString() {
        return "CreateLdapMappingResponse{" +
                "ok=" + ok +
                ", id=" + id +
                ", actionType=" + actionType +
                '}';
    }
}
