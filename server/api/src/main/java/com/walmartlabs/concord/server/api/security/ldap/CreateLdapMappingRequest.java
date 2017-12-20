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

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class CreateLdapMappingRequest implements Serializable {

    @NotNull
    private final String ldapDn;
    private final Set<String> roles;

    @JsonCreator
    public CreateLdapMappingRequest(@JsonProperty("ldapDn") String ldapDn,
                                    @JsonProperty("roles") Set<String> roles) {
        this.ldapDn = ldapDn;
        this.roles = roles;
    }

    public CreateLdapMappingRequest(String ldapDn, String... roles) {
        this(ldapDn, new HashSet<>(Arrays.asList(roles)));
    }

    public String getLdapDn() {
        return ldapDn;
    }

    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public String toString() {
        return "CreateLdapMappingRequest{" +
                "ldapDn='" + ldapDn + '\'' +
                ", roles=" + roles +
                '}';
    }
}
