package com.walmartlabs.concord.server.api.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class UserEntry implements Serializable {

    private final String id;
    private final String name;
    private final Set<String> permissions;

    @JsonCreator
    public UserEntry(@JsonProperty("id") String id,
                     @JsonProperty("name") String name,
                     @JsonProperty("permissions") Set<String> permissions) {
        this.id = id;

        this.name = name;
        this.permissions = permissions;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<String> getPermissions() {
        return permissions;
    }
}
