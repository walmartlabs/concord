package com.walmartlabs.concord.server.boot.filters;

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
import com.walmartlabs.concord.server.RequestUtils;
import com.walmartlabs.concord.server.sdk.metrics.InjectMeter;
import com.walmartlabs.concord.server.security.apikey.ApiKey;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
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
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ConcordAuthenticatingFilter extends AuthenticatingFilter {

    private static final Logger log = LoggerFactory.getLogger(ConcordAuthenticatingFilter.class);

    /**
     * List of URLs on which 'WWW-Authenticate: Basic' is not returned.
     */
    private static final String[] DO_NOT_FORCE_BASIC_AUTH_URLS = {
            "/api/service/console/whoami"
    };

    private final Set<AuthenticationHandler> authenticationHandlers;

    @InjectMeter
    private final Meter successAuths;

    @InjectMeter
    private final Meter failedAuths;

    @Inject
    public ConcordAuthenticatingFilter(Set<AuthenticationHandler> authenticationHandlers,
                                       Meter successAuths,
                                       Meter failedAuths) {

        this.authenticationHandlers = authenticationHandlers;
        this.successAuths = successAuths;
        this.failedAuths = failedAuths;
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

        // run plugins first, they might need to override the default behaviour
        // e.g. use their own `Authorization: Bearer` headers
        for (AuthenticationHandler handler : authenticationHandlers) {
            AuthenticationToken token = handler.createToken(request, response);
            if (token != null) {
                return token;
            }
        }

        // no dice
        return new UsernamePasswordToken();
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        try {
            boolean loggedIn = executeLogin(request, response);

            if (!loggedIn) {
                boolean processed = false;
                for (AuthenticationHandler handler : authenticationHandlers) {
                    processed = handler.onAccessDenied(request, response);
                    if (processed) {
                        break;
                    }
                }

                if (!processed) {
                    HttpServletResponse resp = (HttpServletResponse) response;
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

                    reportAuthSchemes(request, response);
                }
            }

            return loggedIn;
        } catch (Exception e) {
            HttpServletResponse resp = (HttpServletResponse) response;
            sendUnauthorized(resp, e);
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
        log.debug("onLoginFailure ['{}'] -> login failed ({}): {}", token, request.getRemoteAddr(), e.getMessage());
        failedAuths.mark();

        Subject s = ThreadContext.getSubject();
        if (s != null && (s.isRemembered() || s.isAuthenticated())) {
            s.logout();
        }

        return super.onLoginFailure(token, e, request, response);
    }

    private static void sendUnauthorized(HttpServletResponse resp, Throwable t) throws IOException {
        resp.setContentType(MediaType.APPLICATION_JSON);
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        Map<String, String> error = Collections.singletonMap("message", t.getMessage());
        resp.getWriter().write(new ObjectMapper().writeValueAsString(error));
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
        HttpServletRequest req = (HttpServletRequest) request;
        String p = req.getRequestURI();
        for (String s : DO_NOT_FORCE_BASIC_AUTH_URLS) {
            if (p.matches(s)) {
                return;
            }
        }

        String uiRequest = req.getHeader(RequestUtils.UI_REQUEST_HEADER);
        if ("true".equalsIgnoreCase(uiRequest)) {
            // do not send the "WWW-Authenticate" header if the request originates from the UI
            // we don't want the basic auth popup there
            return;
        }

        HttpServletResponse resp = (HttpServletResponse) response;

        String authHeader = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.contains("Basic")) {
            resp.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic");
            resp.addHeader(HttpHeaders.WWW_AUTHENTICATE, "ConcordApiToken");
        }
    }
}
