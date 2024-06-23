package com.walmartlabs.concord.server.boot.filters;

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

import com.walmartlabs.concord.common.secret.SecretUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.security.apikey.ApiKey;
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import com.walmartlabs.concord.server.security.apikey.ApiKeyEntry;
import com.walmartlabs.concord.server.security.sessionkey.SessionKey;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.support.DefaultSubjectContext;

import javax.inject.Inject;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import java.util.Base64;
import java.util.UUID;

import static com.walmartlabs.concord.sdk.Constants.Headers.ENABLE_HTTP_SESSION;

/**
 * Default authentication handler. Handles basic authentication (username/password),
 * API keys and session tokens.
 */
public class ConcordAuthenticationHandler implements AuthenticationHandler {

    private static final String REMEMBER_ME_HEADER = "X-Concord-RememberMe";

    private static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final String BEARER_AUTH_PREFIX = "Bearer ";

    private final ApiKeyDao apiKeyDao;
    private final SecretStoreConfiguration secretCfg;

    @Inject
    public ConcordAuthenticationHandler(ApiKeyDao apiKeyDao, SecretStoreConfiguration secretCfg) {
        this.apiKeyDao = apiKeyDao;
        this.secretCfg = secretCfg;
    }

    @Override
    public AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        HttpServletRequest req = (HttpServletRequest) request;

        // check for a session token next
        if (req.getHeader(Constants.Headers.SESSION_TOKEN) != null) {
            return createFromSessionHeader(req);
        }

        // check for a regular API token
        if (req.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            return createFromAuthHeader(req);
        }

        return null;
    }

    private AuthenticationToken createFromAuthHeader(HttpServletRequest req) {
        // check the 'remember me' status
        boolean rememberMe = Boolean.parseBoolean(req.getHeader(REMEMBER_ME_HEADER));

        String h = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (h.startsWith(BASIC_AUTH_PREFIX)) {
            // enable sessions
            req.setAttribute(DefaultSubjectContext.SESSION_CREATION_ENABLED, Boolean.TRUE);

            return parseBasicAuth(h, rememberMe);
        } else {
            boolean enableSessions = Boolean.parseBoolean(req.getHeader(ENABLE_HTTP_SESSION));
            req.setAttribute(DefaultSubjectContext.SESSION_CREATION_ENABLED, enableSessions);

            if (h.startsWith(BEARER_AUTH_PREFIX)) {
                h = h.substring(BEARER_AUTH_PREFIX.length());
            }

            if (!isApiKeyValid(h)) {
                return null;
            }

            ApiKeyEntry apiKey = apiKeyDao.find(h);
            if (apiKey == null) {
                return new UsernamePasswordToken();
            }

            return new ApiKey(apiKey.getId(), apiKey.getUserId(), h, rememberMe);
        }
    }

    private AuthenticationToken createFromSessionHeader(HttpServletRequest req) {
        // explicitly disable sessions
        req.setAttribute(DefaultSubjectContext.SESSION_CREATION_ENABLED, Boolean.FALSE);

        String h = req.getHeader(Constants.Headers.SESSION_TOKEN);
        return buildSessionToken(h);
    }

    private AuthenticationToken buildSessionToken(String key) {
        return new SessionKey(decryptSessionKey(key));
    }

    private UUID decryptSessionKey(String h) {
        byte[] salt = secretCfg.getSecretStoreSalt();
        byte[] pwd = secretCfg.getServerPwd();

        byte[] ab = SecretUtils.decrypt(Base64.getDecoder().decode(h), pwd, salt);
        return UUID.fromString(new String(ab));
    }

    private static boolean isApiKeyValid(String s) {
        try {
            Base64.getDecoder().decode(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private AuthenticationToken parseBasicAuth(String s, boolean rememberMe) {
        s = s.substring(BASIC_AUTH_PREFIX.length());
        s = new String(Base64.getDecoder().decode(s));

        int idx = s.indexOf(":");
        if (idx + 1 == s.length()) {
            // empty password -> try user name as a session token
            return buildSessionToken(s.substring(0, s.length() - 1));
        }

        if (idx < 0 || idx + 1 >= s.length()) {
            throw new IllegalArgumentException("Invalid basic auth header");
        }

        String username = s.substring(0, idx).trim();
        String password = s.substring(idx + 1);

        return new UsernamePasswordToken(username, password, rememberMe);
    }
}
