package com.walmartlabs.concord.server.api.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleEntry implements Serializable {

    private final UUID id;

    @ConcordKey
    private final String name;

    private final boolean globalReader;
    private final boolean globalWriter;

    @JsonCreator
    public RoleEntry(@JsonProperty("id") UUID id,
                     @JsonProperty("name") String name,
                     @JsonProperty("globalReader") boolean globalReader,
                     @JsonProperty("globalWriter") boolean globalWriter) {

        this.id = id;
        this.name = name;
        this.globalReader = globalReader;
        this.globalWriter = globalWriter;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isGlobalReader() {
        return globalReader;
    }

    public boolean isGlobalWriter() {
        return globalWriter;
    }

    @Override
    public String toString() {
        return "RoleEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", globalReader=" + globalReader +
                ", globalWriter=" + globalWriter +
                '}';
    }
}
