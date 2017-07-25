package com.walmartlabs.concord.server.api.security.ldap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class LdapMappingEntry implements Serializable {

    private final UUID id;
    private final String ldapDn;
    private final Set<String> roles;

    @JsonCreator
    public LdapMappingEntry(@JsonProperty("id") UUID id,
                            @JsonProperty("ldapDn") String ldapDn,
                            @JsonProperty("roles") Set<String> roles) {
        this.id = id;
        this.ldapDn = ldapDn;
        this.roles = roles;
    }

    public UUID getId() {
        return id;
    }

    public String getLdapDn() {
        return ldapDn;
    }

    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public String toString() {
        return "LdapMappingEntry{" +
                "id=" + id +
                ", ldapDn='" + ldapDn + '\'' +
                ", roles=" + roles +
                '}';
    }
}
