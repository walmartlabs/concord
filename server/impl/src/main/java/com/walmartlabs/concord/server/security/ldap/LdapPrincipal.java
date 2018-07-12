package com.walmartlabs.concord.server.security.ldap;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.server.security.PrincipalUtils;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public class LdapPrincipal implements Serializable {

    private final String username;
    private final String nameInNamespace;
    private final String displayName;
    private final String email;
    private final Set<String> groups;
    private final Map<String, String> attributes;

    public LdapPrincipal(String username, String nameInNamespace, String displayName, String email, Set<String> groups, Map<String, String> attributes) {
        this.username = username;
        this.nameInNamespace = nameInNamespace;
        this.displayName = displayName;
        this.email = email;
        this.groups = groups;
        this.attributes = attributes;
    }

    public static LdapPrincipal getCurrent() {
        return PrincipalUtils.getCurrent(LdapPrincipal.class);
    }

    public String getUsername() {
        return username;
    }

    public String getNameInNamespace() {
        return nameInNamespace;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public Set<String> getGroups() {
        return groups;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return "LdapPrincipal{" +
                "username='" + username + '\'' +
                ", nameInNamespace='" + nameInNamespace + '\'' +
                ", displayName='" + displayName + '\'' +
                ", email='" + email + '\'' +
                ", groups=" + groups +
                ", attributes=" + attributes +
                '}';
    }
}
