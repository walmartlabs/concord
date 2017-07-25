package com.walmartlabs.concord.server.security;

import com.walmartlabs.concord.server.security.ldap.LdapInfo;

import java.io.Serializable;
import java.util.UUID;

public class UserPrincipal implements Serializable {

    private final String realm;
    private final UUID id;
    private final String username;
    private final LdapInfo ldapInfo;

    public UserPrincipal(String realm, UUID id, String username) {
        this(realm, id, username, null);
    }

    public UserPrincipal(String realm, UUID id, String username, LdapInfo ldapInfo) {
        this.realm = realm;
        this.id = id;
        this.username = username;
        this.ldapInfo = ldapInfo;
    }

    public String getRealm() {
        return realm;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public LdapInfo getLdapInfo() {
        return ldapInfo;
    }
}
