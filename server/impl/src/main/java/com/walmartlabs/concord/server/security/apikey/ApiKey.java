package com.walmartlabs.concord.server.security.apikey;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.subject.Subject;

import java.util.UUID;

public class ApiKey implements AuthenticationToken {

    public static String getCurrentKey() {
        Subject subject = SecurityUtils.getSubject();

        ApiKey k = null;
        for (Object p : subject.getPrincipals()) {
            if (p instanceof ApiKey) {
                k = (ApiKey) p;
            }
        }

        if (k == null) {
            throw new UnauthorizedException("API key not found");
        }

        return k.getKey();
    }

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
