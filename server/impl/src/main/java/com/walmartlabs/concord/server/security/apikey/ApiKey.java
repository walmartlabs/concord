package com.walmartlabs.concord.server.security.apikey;

import org.apache.shiro.authc.AuthenticationToken;

public class ApiKey implements AuthenticationToken {

    private final String userId;
    private final String key;

    public ApiKey(String userId, String key) {
        this.userId = userId;
        this.key = key;
    }

    public String getUserId() {
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
}
