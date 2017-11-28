package com.walmartlabs.concord.server.api.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class UserEntry implements Serializable {

    private final UUID id;
    private final String name;
    private final Set<String> permissions;
    private final Set<OrganizationEntry> orgs;
    private final boolean admin;

    @JsonCreator
    public UserEntry(@JsonProperty("id") UUID id,
                     @JsonProperty("name") String name,
                     @JsonProperty("permissions") Set<String> permissions,
                     @JsonProperty("orgs") Set<OrganizationEntry> orgs,
                     @JsonProperty("admin") boolean admin) {

        this.id = id;
        this.name = name;
        this.permissions = permissions;
        this.orgs = orgs;
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

    public Set<OrganizationEntry> getOrgs() {
        return orgs;
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
                ", orgs=" + orgs +
                ", admin=" + admin +
                '}';
    }
}
