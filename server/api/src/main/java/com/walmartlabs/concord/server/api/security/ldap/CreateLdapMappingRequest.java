package com.walmartlabs.concord.server.api.security.ldap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class CreateLdapMappingRequest implements Serializable {

    @NotNull
    private final String ldapDn;
    private final Set<String> roles;

    @JsonCreator
    public CreateLdapMappingRequest(@JsonProperty("ldapDn") String ldapDn,
                                    @JsonProperty("roles") Set<String> roles) {
        this.ldapDn = ldapDn;
        this.roles = roles;
    }

    public String getLdapDn() {
        return ldapDn;
    }

    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public String toString() {
        return "CreateLdapMappingRequest{" +
                "ldapDn='" + ldapDn + '\'' +
                ", roles=" + roles +
                '}';
    }
}
