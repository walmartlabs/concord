package com.walmartlabs.concord.server.api.team;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class TeamUserEntry implements Serializable {

    private final UUID id;
    private final String username;

    @JsonCreator
    public TeamUserEntry(@JsonProperty("id") UUID id,
                         @JsonProperty("username") String username) {
        this.id = id;
        this.username = username;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return "TeamUserEntry{" +
                "id=" + id +
                ", username='" + username + '\'' +
                '}';
    }
}
