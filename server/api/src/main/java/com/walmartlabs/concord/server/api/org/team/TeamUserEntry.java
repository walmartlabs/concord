package com.walmartlabs.concord.server.api.org.team;

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
    private final TeamRole role;

    public TeamUserEntry(String username, TeamRole role) {
        this(null, username, role);
    }

    @JsonCreator
    public TeamUserEntry(@JsonProperty("id") UUID id,
                         @JsonProperty("username") String username,
                         @JsonProperty("role") TeamRole role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public TeamRole getRole() {
        return role;
    }

    @Override
    public String toString() {
        return "TeamUserEntry{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", role=" + role +
                '}';
    }
}
