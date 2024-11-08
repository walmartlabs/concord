package com.walmartlabs.concord.server.security;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.server.boot.filters.AuthenticationHandler;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.support.DefaultSubjectContext;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import java.util.Base64;

import static com.walmartlabs.concord.sdk.Constants.Headers.REMEMBER_ME_HEADER;

/**
 * Handles basic authentication (username/password).
 */
public class BasicAuthenticationHandler implements AuthenticationHandler {

    private static final String BASIC_AUTH_PREFIX = "Basic ";

    @Override
    public AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        var req = (HttpServletRequest) request;

        if (req.getHeader(HttpHeaders.AUTHORIZATION) == null) {
            return null;
        }

        // check the 'remember me' status
        var rememberMe = Boolean.parseBoolean(req.getHeader(REMEMBER_ME_HEADER));

        var auth = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || auth.isBlank()) {
            return null;
        }

        if (!auth.startsWith(BASIC_AUTH_PREFIX)) {
            return null;
        }

        auth = auth.substring(BASIC_AUTH_PREFIX.length());
        auth = new String(Base64.getDecoder().decode(auth));

        var idx = auth.indexOf(":");
        if (idx + 1 == auth.length()) {
            throw new IllegalArgumentException("Invalid basic auth header");
        }

        if (idx < 0 || idx + 1 >= auth.length()) {
            throw new IllegalArgumentException("Invalid basic auth header");
        }

        var username = auth.substring(0, idx).trim();
        var password = auth.substring(idx + 1);

        // enable sessions
        req.setAttribute(DefaultSubjectContext.SESSION_CREATION_ENABLED, true);

        return new UsernamePasswordToken(username, password, rememberMe);
    }
}
