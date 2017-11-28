package com.walmartlabs.concord.server.api.org;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class OrganizationEntry implements Serializable {

    private final UUID id;

    @ConcordKey
    private final String name;

    public OrganizationEntry(String name) {
        this(null, name);
    }

    @JsonCreator
    public OrganizationEntry(@JsonProperty("id") UUID id,
                             @JsonProperty("name") String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "OrganizationEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
