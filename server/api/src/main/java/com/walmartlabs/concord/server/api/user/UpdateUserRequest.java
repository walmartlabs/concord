package com.walmartlabs.concord.server.api.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Set;

public class UpdateUserRequest implements Serializable {

    private final Set<String> permissions;

    @JsonCreator
    public UpdateUserRequest(@JsonProperty("permissions") Set<String> permissions) {
        this.permissions = permissions;
    }

    public Set<String> getPermissions() {
        return permissions;
    }
}
