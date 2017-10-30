package com.walmartlabs.concord.server.security;

import com.walmartlabs.concord.server.security.ldap.LdapInfo;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

import java.io.Serializable;
import java.util.UUID;

public class UserPrincipal implements Serializable {

    public static UserPrincipal getCurrent() {
        Subject subject = SecurityUtils.getSubject();
        return (UserPrincipal) subject.getPrincipal();
    }

    private final String realm;
    private final UUID id;
    private final String username;
    private final LdapInfo ldapInfo;
    private final boolean admin;

    public UserPrincipal(String realm, UUID id, String username, LdapInfo ldapInfo, boolean admin) {
        this.realm = realm;
        this.id = id;
        this.username = username;
        this.ldapInfo = ldapInfo;
        this.admin = admin;
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

    public boolean isAdmin() {
        return admin;
    }

    @Override
    public String toString() {
        return "UserPrincipal{" +
                "realm='" + realm + '\'' +
                ", id=" + id +
                ", username='" + username + '\'' +
                ", ldapInfo=" + ldapInfo +
                ", admin=" + admin +
                '}';
    }
}
