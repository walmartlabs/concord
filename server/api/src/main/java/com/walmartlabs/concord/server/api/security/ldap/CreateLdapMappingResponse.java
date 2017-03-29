package com.walmartlabs.concord.server.api.security.ldap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordId;

import java.io.Serializable;

public class CreateLdapMappingResponse implements Serializable {

    private final boolean ok = true;

    @ConcordId
    private final String id;

    private final boolean created;

    @JsonCreator
    public CreateLdapMappingResponse(@JsonProperty("id") String id,
                                     @JsonProperty("created") boolean created) {
        this.id = id;
        this.created = created;
    }

    public String getId() {
        return id;
    }

    public boolean isOk() {
        return ok;
    }

    public boolean isCreated() {
        return created;
    }

    @Override
    public String toString() {
        return "CreateLdapMappingResponse{" +
                "ok=" + ok +
                ", id='" + id + '\'' +
                ", created=" + created +
                '}';
    }
}
