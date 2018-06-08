package com.walmartlabs.concord.server.api.user;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

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
    private final Set<OrganizationEntry> orgs;
    private final boolean admin;
    private final UserType type;
    private final Set<RoleEntry> roles;

    @JsonCreator
    public UserEntry(@JsonProperty("id") UUID id,
                     @JsonProperty("name") String name,
                     @JsonProperty("orgs") Set<OrganizationEntry> orgs,
                     @JsonProperty("admin") boolean admin,
                     @JsonProperty("type") UserType type,
                     @JsonProperty("roles") Set<RoleEntry> roles) {

        this.id = id;
        this.name = name;
        this.orgs = orgs;
        this.admin = admin;
        this.type = type;
        this.roles = roles;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<OrganizationEntry> getOrgs() {
        return orgs;
    }

    public boolean isAdmin() {
        return admin;
    }

    public UserType getType() {
        return type;
    }

    public Set<RoleEntry> getRoles() {
        return roles;
    }

    @Override
    public String toString() {
        return "UserEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", orgs=" + orgs +
                ", admin=" + admin +
                ", type=" + type +
                ", roles=" + roles +
                '}';
    }
}
