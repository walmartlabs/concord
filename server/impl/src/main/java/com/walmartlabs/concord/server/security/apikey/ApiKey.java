package com.walmartlabs.concord.server.security.apikey;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;

public class ApiKey implements AuthenticationToken {

    public static String getCurrentApiKey() {
        Subject subject = SecurityUtils.getSubject();

        ApiKey k = null;
        for (Object p : subject.getPrincipals()) {
            if (p instanceof ApiKey) {
                k = (ApiKey) p;
            }
        }

        if (k == null) {
            throw new SecurityException("API token not found");
        }

        return k.getKey();
    }

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
