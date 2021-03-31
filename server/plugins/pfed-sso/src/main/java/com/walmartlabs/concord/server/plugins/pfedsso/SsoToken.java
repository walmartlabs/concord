package com.walmartlabs.concord.server.plugins.pfedsso;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import org.apache.shiro.authc.AuthenticationToken;

import java.util.Set;

public class SsoToken implements AuthenticationToken {

    private final String username;
    private final String domain;
    private final String displayName;
    private final String mail;
    private final String userPrincipalName;
    private final String nameInNamespace;
    private final Set<String> groups;

    public SsoToken(String username, String domain, String displayName, String mail, String userPrincipalName, String nameInNamespace, Set<String> groups) {
        this.username = username;
        this.domain = domain;
        this.displayName = displayName;
        this.mail = mail;
        this.userPrincipalName = userPrincipalName;
        this.nameInNamespace = nameInNamespace;
        this.groups = groups;
    }

    public String getUsername() {
        return username;
    }

    public String getDomain() {
        return domain;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getMail() {
        return mail;
    }

    public String getNameInNamespace() {
        return nameInNamespace;
    }

    public String getUserPrincipalName() {
        return userPrincipalName;
    }

    public Set<String> getGroups() {
        return groups;
    }

    @Override
    public Object getPrincipal() {
        return username + "@" + domain;
    }

    @Override
    public Object getCredentials() {
        return getPrincipal();
    }

    @Override
    public String toString() {
        return "SsoToken{" +
                "username='" + username + '\'' +
                ", domain='" + domain + '\'' +
                ", displayName='" + displayName + '\'' +
                ", mail='" + mail + '\'' +
                ", userPrincipalName='" + userPrincipalName + '\'' +
                ", nameInNamespace='" + nameInNamespace + '\'' +
                ", groups=" + groups +
                '}';
    }
}
