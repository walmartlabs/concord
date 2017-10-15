package com.walmartlabs.concord.server.security;

import com.walmartlabs.concord.server.security.ldap.LdapInfo;
import com.walmartlabs.concord.server.user.TeamEntry;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

public class UserPrincipal implements Serializable {

    private final String realm;
    private final UUID id;
    private final String username;
    private final LdapInfo ldapInfo;
    private final Set<TeamEntry> teams;

    public UserPrincipal(String realm, UUID id, String username, Set<TeamEntry> teams) {
        this(realm, id, username, teams, null);
    }

    public UserPrincipal(String realm, UUID id, String username, Set<TeamEntry> teams, LdapInfo ldapInfo) {
        this.realm = realm;
        this.id = id;
        this.username = username;
        this.teams = teams;
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

    public Set<TeamEntry> getTeams() {
        return teams;
    }

    public LdapInfo getLdapInfo() {
        return ldapInfo;
    }
}
