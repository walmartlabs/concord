package com.walmartlabs.concord.server.security.apikey;

import org.apache.shiro.authc.AuthenticationToken;

import java.util.UUID;

public class ApiKey implements AuthenticationToken {

    private final UUID userId;
    private final String key;

    public ApiKey(UUID userId, String key) {
        this.userId = userId;
        this.key = key;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getKey() {
        return key;
    }

    @Override
    public Object getPrincipal() {
        return getUserId();
    }

    @Override
    public Object getCredentials() {
        return getKey();
    }

    @Override
    public String toString() {
        return "ApiKey{" +
                "userId=" + userId +
                ", key='" + key + '\'' +
                '}';
    }
}
