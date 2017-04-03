package com.walmartlabs.concord.server.api.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class RoleEntry implements Serializable {

    @NotNull
    @ConcordKey
    private final String name;
    private final String description;
    private final Set<String> permissions;

    @JsonCreator
    public RoleEntry(@JsonProperty("name") String name,
                     @JsonProperty("description") String description,
                     @JsonProperty("permissions") Set<String> permissions) {

        this.name = name;
        this.description = description;
        this.permissions = permissions;
    }

    public RoleEntry(String name, String description, String... permissions) {
        this(name, description, new HashSet<>(Arrays.asList(permissions)));
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    @Override
    public String toString() {
        return "RoleEntry{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", permissions=" + permissions +
                '}';
    }
}
