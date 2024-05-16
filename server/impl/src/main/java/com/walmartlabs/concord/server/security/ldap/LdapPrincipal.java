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

import com.walmartlabs.concord.server.security.SecurityUtils;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * <b>Note:</b> this class is serialized when user principals are stored in
 * the process state. It must maintain backward compatibility.
 */
public class LdapPrincipal implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String username;
    private final String domain;
    private final String nameInNamespace;
    private final String userPrincipalName;
    private final String displayName;
    private final String email;
    private final Set<String> groups;
    private final Map<String, Object> attributes;

    public LdapPrincipal(String username,
                         String domain,
                         String nameInNamespace,
                         String userPrincipalName,
                         String displayName,
                         String email,
                         Set<String> groups,
                         Map<String, Object> attributes) {

        this.username = username;
        this.domain = domain;
        this.nameInNamespace = nameInNamespace;
        this.userPrincipalName = userPrincipalName;
        this.displayName = displayName;
        this.email = email;
        this.groups = groups;
        this.attributes = attributes;
    }

    public static LdapPrincipal getCurrent() {
        return SecurityUtils.getCurrent(LdapPrincipal.class);
    }

    public String getUsername() {
        return username;
    }

    public String getDomain() {
        return domain;
    }

    public String getNameInNamespace() {
        return nameInNamespace;
    }

    public String getUserPrincipalName() {
        return userPrincipalName;
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

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return "LdapPrincipal{" +
                "username='" + username + '\'' +
                ", domain='" + domain + '\'' +
                ", nameInNamespace='" + nameInNamespace + '\'' +
                ", userPrincipalName='" + userPrincipalName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", email='" + email + '\'' +
                ", groups=" + groups +
                ", attributes=" + attributes +
                '}';
    }
}
