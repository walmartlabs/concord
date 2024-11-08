package com.walmartlabs.concord.server.security.apikey;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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
import org.apache.shiro.subject.support.DefaultSubjectContext;

import javax.inject.Inject;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import java.util.Base64;

import static com.walmartlabs.concord.sdk.Constants.Headers.ENABLE_HTTP_SESSION;
import static com.walmartlabs.concord.sdk.Constants.Headers.REMEMBER_ME_HEADER;
import static java.util.Objects.requireNonNull;

/**
 * Handles the regular Concord API key authentication.
 */
public class ApiKeyAuthenticationHandler implements AuthenticationHandler {

    private static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final String BEARER_AUTH_PREFIX = "Bearer ";

    private final ApiKeyDao dao;

    @Inject
    public ApiKeyAuthenticationHandler(ApiKeyDao dao) {
        this.dao = requireNonNull(dao);
    }

    @Override
    public AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        var req = (HttpServletRequest) request;

        var auth = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || auth.isBlank()) {
            return null;
        }

        if (auth.startsWith(BASIC_AUTH_PREFIX)) {
            // not our case
            return null;
        }

        // Concord API keys can be sent both as plain "Authentication: <token>"
        // and as "Authentication: Bearer <token>"

        if (auth.startsWith(BEARER_AUTH_PREFIX)) {
            auth = auth.substring(BEARER_AUTH_PREFIX.length());
        }

        if (!isValidBase64(auth)) {
            return null;
        }

        var apiKey = dao.find(auth);
        if (apiKey == null) {
            return null;
        }

        var enableSessions = Boolean.parseBoolean(req.getHeader(ENABLE_HTTP_SESSION));
        req.setAttribute(DefaultSubjectContext.SESSION_CREATION_ENABLED, enableSessions);

        var rememberMe = Boolean.parseBoolean(req.getHeader(REMEMBER_ME_HEADER));
        return new ApiKey(apiKey.getId(), apiKey.getUserId(), auth, rememberMe);
    }

    private static boolean isValidBase64(String s) {
        try {
            Base64.getDecoder().decode(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
