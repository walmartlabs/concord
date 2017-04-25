package com.walmartlabs.concord.server.security.ldap;

import java.io.Serializable;
import java.util.Set;

public final class LdapInfo implements Serializable {

    private final String displayName;
    private final Set<String> groups;

    public LdapInfo(String displayName, Set<String> groups) {
        this.displayName = displayName;
        this.groups = groups;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<String> getGroups() {
        return groups;
    }
}
