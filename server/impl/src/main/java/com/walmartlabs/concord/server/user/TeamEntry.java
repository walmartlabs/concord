package com.walmartlabs.concord.server.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

public class TeamEntry implements Serializable {

    private final UUID id;
    private final String name;

    @JsonCreator
    public TeamEntry(@JsonProperty("id") UUID id,
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
        return "TeamEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
