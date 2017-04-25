package com.walmartlabs.concord.server.security.ldap;

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
