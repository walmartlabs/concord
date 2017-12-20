package com.walmartlabs.concord.server.security.ldap;

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

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public final class LdapInfo implements Serializable {

    private final String username;
    private final String displayName;
    private final Set<String> groups;
    private final Map<String, String> attributes;

    public LdapInfo(String username, String displayName, Set<String> groups, Map<String, String> attributes) {
        this.username = username;
        this.displayName = displayName;
        this.groups = groups;
        this.attributes = attributes;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<String> getGroups() {
        return groups;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }
}
