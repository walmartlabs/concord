package com.walmartlabs.concord.server.api.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class RoleEntry implements Serializable {

    private final String name;
    private final Set<String> permissions;

    @JsonCreator
    public RoleEntry(@JsonProperty("name") String name,
                     @JsonProperty("permissions") Set<String> permissions) {

        this.name = name;
        this.permissions = permissions;
    }

    public String getName() {
        return name;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    @Override
    public String toString() {
        return "RoleEntry{" +
                "name='" + name + '\'' +
                ", permissions=" + permissions +
                '}';
    }
}
