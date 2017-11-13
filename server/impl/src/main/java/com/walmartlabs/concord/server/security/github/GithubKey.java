package com.walmartlabs.concord.server.security.github;

import org.apache.shiro.authc.AuthenticationToken;

public class GithubKey implements AuthenticationToken {

    private final String key;

    public GithubKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    @Override
    public Object getPrincipal() {
        return getKey();
    }

    @Override
    public Object getCredentials() {
        return getKey();
    }

    @Override
    public String toString() {
        return "GithubKey{" +
                "key='" + key + '\'' +
                '}';
    }
}