package com.walmartlabs.concord.server.security;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.codahale.metrics.Meter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.sdk.metrics.InjectMeter;
import com.walmartlabs.concord.server.org.secret.SecretUtils;
import com.walmartlabs.concord.server.security.apikey.ApiKey;
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import com.walmartlabs.concord.server.security.sessionkey.SessionKey;
import com.walmartlabs.concord.server.security.sso.SsoHandler;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.DefaultSubjectContext;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class ConcordAuthenticatingFilter extends AuthenticatingFilter {

    private static final Logger log = LoggerFactory.getLogger(ConcordAuthenticatingFilter.class);

    private static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;
    private static final String SESSION_TOKEN_HEADER = "X-Concord-SessionToken";
    private static final String REMEMBER_ME_HEADER = "X-Concord-RememberMe";
    private static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final String BEARER_AUTH_PREFIX = "Bearer ";

    /**
     * List of URLs on which 'WWW-Authenticate: Basic' is not returned.
     */
    private static final String[] DO_NOT_FORCE_BASIC_AUTH_URLS = {
            "/api/service/console/whoami"
    };

    private final ApiKeyDao apiKeyDao;
    private final SecretStoreConfiguration secretCfg;

    @InjectMeter
    private final Meter successAuths;

    @InjectMeter
    private final Meter failedAuths;

    private final SsoHandler ssoHandler;

    @Inject
    public ConcordAuthenticatingFilter(ApiKeyDao apiKeyDao,
                                       SecretStoreConfiguration secretCfg,
                                       Meter successAuths,
                                       Meter failedAuths,
                                       SsoHandler ssoHandler) {

        this.apiKeyDao = apiKeyDao;
        this.secretCfg = secretCfg;
        this.successAuths = successAuths;
        this.failedAuths = failedAuths;
        this.ssoHandler = ssoHandler;
    }

    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        Subject subject = SecurityUtils.getSubject();
        if (subject != null && subject.isRemembered()) {
            AuthenticationToken t = getFirstToken(subject);
            if (t != null) {
                return t;
            }
        }

        HttpServletRequest req = WebUtils.toHttp(request);

        // session header takes precedence
        if (req.getHeader(SESSION_TOKEN_HEADER) != null) {
            return createFromSessionHeader(req);
        }

        if (req.getHeader(AUTHORIZATION_HEADER) != null) {
            return createFromAuthHeader(req);
        }

        AuthenticationToken token = ssoHandler.createToken(request, response);
        if (token != null) {
            return token;
        }

        return new UsernamePasswordToken();
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        try {
            boolean loggedIn = executeLogin(request, response);

            if (!loggedIn) {
                boolean processed = ssoHandler.onAccessDenied(request, response);
                if (!processed) {
                    HttpServletResponse resp = WebUtils.toHttp(response);
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

                    reportAuthSchemes(request, response);
                }
            }

            return loggedIn;
        } catch (Exception e) {
            HttpServletResponse resp = WebUtils.toHttp(response);
            writeError(resp, e, HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    @Override
    protected boolean onLoginSuccess(AuthenticationToken token, Subject subject, ServletRequest request, ServletResponse response) throws Exception {
        successAuths.mark();
        return super.onLoginSuccess(token, subject, request, response);
    }

    @Override
    protected boolean onLoginFailure(AuthenticationToken token, AuthenticationException e, ServletRequest request, ServletResponse response) {
        log.warn("onLoginFailure ['{}'] -> login failed ({}): {}", token, request.getRemoteAddr(), e.getMessage());
        failedAuths.mark();

        Subject s = SecurityUtils.getSubject();
        if (s.isRemembered()) {
            s.logout();
        }

        return super.onLoginFailure(token, e, request, response);
    }

    private AuthenticationToken createFromAuthHeader(HttpServletRequest req) {
        String h = req.getHeader(AUTHORIZATION_HEADER);

        // enable sessions
        req.setAttribute(DefaultSubjectContext.SESSION_CREATION_ENABLED, Boolean.TRUE);

        // check the 'remember me' status
        boolean rememberMe = Boolean.parseBoolean(req.getHeader(REMEMBER_ME_HEADER));

        AuthenticationToken token;
        if (h.startsWith(BASIC_AUTH_PREFIX)) {
            token = parseBasicAuth(h, req, rememberMe);
        } else {
            if (h.startsWith(BEARER_AUTH_PREFIX)) {
                h = h.substring(BASIC_AUTH_PREFIX.length() + 1);
            }

            validateApiKey(h);

            UUID userId = apiKeyDao.findUserId(h);
            if (userId == null) {
                return new UsernamePasswordToken();
            }

            token = new ApiKey(userId, h, rememberMe);
        }

        return token;
    }

    private AuthenticationToken createFromSessionHeader(HttpServletRequest req) {
        String h = req.getHeader(SESSION_TOKEN_HEADER);
        return buildSessionToken(req, h);
    }

    private AuthenticationToken buildSessionToken(HttpServletRequest req, String key) {
        AuthenticationToken t = new SessionKey(decryptSessionKey(key));
        // enable sessions, assume the clients handle the session cookie correctly
        req.setAttribute(DefaultSubjectContext.SESSION_CREATION_ENABLED, Boolean.TRUE);
        return t;
    }

    private UUID decryptSessionKey(String h) {
        byte[] salt = secretCfg.getSecretStoreSalt();
        byte[] pwd = secretCfg.getServerPwd();

        byte[] ab = SecretUtils.decrypt(Base64.getDecoder().decode(h), pwd, salt);
        return UUID.fromString(new String(ab));
    }

    private static void writeError(HttpServletResponse resp, Throwable t, int code) throws IOException {
        resp.setContentType(MediaType.APPLICATION_JSON);
        resp.setStatus(code);

        Map<String, String> error = Collections.singletonMap("message", t.getMessage());
        resp.getWriter().write(new ObjectMapper().writeValueAsString(error));
    }

    private static void validateApiKey(String s) {
        try {
            Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw new AuthenticationException("Invalid API token: " + e.getMessage());
        }
    }

    private static AuthenticationToken getFirstToken(Subject subject) {
        PrincipalCollection principals = subject.getPrincipals();
        if (principals == null || principals.isEmpty()) {
            return null;
        }

        AuthenticationToken t = principals.oneByType(UsernamePasswordToken.class);
        if (t != null) {
            return t;
        }

        return principals.oneByType(ApiKey.class);
    }

    private static void reportAuthSchemes(ServletRequest request, ServletResponse response) {
        HttpServletRequest req = WebUtils.toHttp(request);
        String p = req.getRequestURI();
        for (String s : DO_NOT_FORCE_BASIC_AUTH_URLS) {
            if (p.matches(s)) {
                return;
            }
        }

        HttpServletResponse resp = WebUtils.toHttp(response);

        String authHeader = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.contains("Basic")) {
            resp.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic");
            resp.addHeader(HttpHeaders.WWW_AUTHENTICATE, "ConcordApiToken");
        }
    }

    private AuthenticationToken parseBasicAuth(String s, HttpServletRequest request, boolean rememberMe) {
        s = s.substring(BASIC_AUTH_PREFIX.length());
        s = new String(Base64.getDecoder().decode(s));

        int idx = s.indexOf(":");
        if (idx + 1 == s.length()) {
            // empty password -> try user name as a session token
            return buildSessionToken(request, s.substring(0, s.length() - 1));
        }

        if (idx < 0 || idx + 1 >= s.length()) {
            throw new IllegalArgumentException("Invalid basic auth header");
        }

        String username = s.substring(0, idx).trim();
        String password = s.substring(idx + 1);

        return new UsernamePasswordToken(username, password, rememberMe);
    }
}
