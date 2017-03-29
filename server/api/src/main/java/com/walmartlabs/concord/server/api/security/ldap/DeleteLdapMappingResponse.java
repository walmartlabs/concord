package com.walmartlabs.concord.server.api.security.ldap;

import java.io.Serializable;

public class DeleteLdapMappingResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "DeleteLdapMappingResponse{" +
                "ok=" + ok +
                '}';
    }
}
