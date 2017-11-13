package com.walmartlabs.concord.server.security.sessionkey;

import org.apache.shiro.authc.AuthenticationToken;

import java.util.UUID;

public class SessionKey implements AuthenticationToken {

    private final UUID instanceId;

    public SessionKey(UUID instanceId) {
        this.instanceId = instanceId;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    @Override
    public Object getPrincipal() {
        return getInstanceId();
    }

    @Override
    public Object getCredentials() {
        return getInstanceId();
    }


    @Override
    public String toString() {
        return "SessionKey{" +
                ", instanceId=" + instanceId +
                '}';
    }
}
