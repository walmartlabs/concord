package com.walmartlabs.concord.server.api.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordUsername;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Set;

public class CreateUserRequest implements Serializable {

    @NotNull
    @ConcordUsername
    private final String username;

    private final Set<String> permissions;

    private final Set<String> teams;

    public CreateUserRequest(String username,
                             Set<String> permissions) {
        this(username, permissions, null);
    }

    @JsonCreator
    public CreateUserRequest(@JsonProperty("username") String username,
                             @JsonProperty("permissions") Set<String> permissions,
                             @JsonProperty("teams") Set<String> teams) {

        this.username = username;
        this.permissions = permissions;
        this.teams = teams;
    }

    public String getUsername() {
        return username;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public Set<String> getTeams() {
        return teams;
    }

    @Override
    public String toString() {
        return "CreateUserRequest{" +
                "username='" + username + '\'' +
                ", permissions=" + permissions +
                ", teams=" + teams +
                '}';
    }
}
