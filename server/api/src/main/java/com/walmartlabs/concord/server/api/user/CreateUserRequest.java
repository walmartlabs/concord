package com.walmartlabs.concord.server.api.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Set;

public class CreateUserRequest implements Serializable {

    @NotNull
    private final String username;

    private final Set<String> permissions;

    @JsonCreator
    public CreateUserRequest(@JsonProperty("username") String username, @JsonProperty("permissions") Set<String> permissions) {
        this.username = username;
        this.permissions = permissions;
    }

    public String getUsername() {
        return username;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    @Override
    public String toString() {
        return "CreateUserRequest{" +
                "username='" + username + '\'' +
                ", permissions=" + permissions +
                '}';
    }
}
