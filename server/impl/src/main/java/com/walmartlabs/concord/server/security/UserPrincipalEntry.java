package com.walmartlabs.concord.server.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Set;

public class UserPrincipalEntry implements Serializable {

    private final Set<String> realmNames;
    private final UserPrincipal principal;

    @JsonCreator
    public UserPrincipalEntry(@JsonProperty("realms") Set<String> realmNames,
                              @JsonProperty("principal") UserPrincipal principal) {
        this.realmNames = realmNames;
        this.principal = principal;
    }

    public Set<String> getRealmNames() {
        return realmNames;
    }

    public UserPrincipal getPrincipal() {
        return principal;
    }
}
