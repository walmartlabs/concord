package com.walmartlabs.concord.server.api.security.ldap;

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

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class LdapMappingEntry implements Serializable {

    private final UUID id;
    private final String ldapDn;
    private final Set<String> roles;

    @JsonCreator
    public LdapMappingEntry(@JsonProperty("id") UUID id,
                            @JsonProperty("ldapDn") String ldapDn,
                            @JsonProperty("roles") Set<String> roles) {
        this.id = id;
        this.ldapDn = ldapDn;
        this.roles = roles;
    }

    public UUID getId() {
        return id;
    }

    public String getLdapDn() {
        return ldapDn;
    }

    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public String toString() {
        return "LdapMappingEntry{" +
                "id=" + id +
                ", ldapDn='" + ldapDn + '\'' +
                ", roles=" + roles +
                '}';
    }
}
