package com.walmartlabs.concord.server.boot.filters;

import org.apache.shiro.authc.AuthenticationException;

public class ConcordAuthenticationException extends AuthenticationException {

    public ConcordAuthenticationException(String message) {
        super(message);
    }


}
