package com.walmartlabs.concord.server.security.ldap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

public class SyncUserLdapGroupRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull
    private final String username;

    private final String userDomain;

    @JsonCreator
    public SyncUserLdapGroupRequest(@JsonProperty("username") String username, @JsonProperty("userDomain") String userDomain) {
        this.username = username;
        this.userDomain = userDomain;
    }

    public String getUsername() {
        return username;
    }

    public String getUserDomain() {
        return userDomain;
    }

    @Override
    public String toString() {
        return "SyncUserLdapGroupRequest{" +
                "username='" + username + '\'' +
                ", userDomain='" + userDomain + '\'' +
                '}';
    }
}
