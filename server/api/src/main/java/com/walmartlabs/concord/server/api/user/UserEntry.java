package com.walmartlabs.concord.server.api.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.team.TeamEntry;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class UserEntry implements Serializable {

    private final UUID id;
    private final String name;
    private final Set<String> permissions;
    private final Set<TeamEntry> teams;
    private final boolean admin;

    @JsonCreator
    public UserEntry(@JsonProperty("id") UUID id,
                     @JsonProperty("name") String name,
                     @JsonProperty("permissions") Set<String> permissions,
                     @JsonProperty("teams") Set<TeamEntry> teams,
                     @JsonProperty("admin") boolean admin) {

        this.id = id;
        this.name = name;
        this.permissions = permissions;
        this.teams = teams;
        this.admin = admin;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public Set<TeamEntry> getTeams() {
        return teams;
    }

    public boolean isAdmin() {
        return admin;
    }

    @Override
    public String toString() {
        return "UserEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", permissions=" + permissions +
                ", teams=" + teams +
                ", admin=" + admin +
                '}';
    }
}
