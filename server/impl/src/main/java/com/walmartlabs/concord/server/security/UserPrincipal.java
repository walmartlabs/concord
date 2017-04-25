package com.walmartlabs.concord.server.security;

import com.walmartlabs.concord.server.security.ldap.LdapInfo;

import java.io.Serializable;

public class UserPrincipal implements Serializable {

    private final String realm;
    private final String id;
    private final String username;
    private final LdapInfo ldapInfo;

    public UserPrincipal(String realm, String id, String username) {
        this(realm, id, username, null);
    }

    public UserPrincipal(String realm, String id, String username, LdapInfo ldapInfo) {
        this.realm = realm;
        this.id = id;
        this.username = username;
        this.ldapInfo = ldapInfo;
    }

    public String getRealm() {
        return realm;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public LdapInfo getLdapInfo() {
        return ldapInfo;
    }
}
