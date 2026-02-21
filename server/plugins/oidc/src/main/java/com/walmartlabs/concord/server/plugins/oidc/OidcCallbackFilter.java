package com.walmartlabs.concord.server.plugins.oidc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

public class OidcCallbackFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(OidcCallbackFilter.class);

    public static final String URL = "/api/service/oidc/callback";
    private static final String SESSION_STATE_KEY = "OIDC_STATE";
    private static final String SESSION_REDIRECT_KEY = "OIDC_REDIRECT_URL";
    private static final String SESSION_PROFILE_KEY = "OIDC_USER_PROFILE";

    private final PluginConfiguration cfg;
    private final OidcService oidcService;

    @Inject
    public OidcCallbackFilter(PluginConfiguration cfg, OidcService oidcService) {
        this.cfg = cfg;
        this.oidcService = oidcService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException {
        var req = (HttpServletRequest) request;
        var resp = (HttpServletResponse) response;

        if (!cfg.isEnabled()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "OIDC disabled");
            return;
        }

        var session = req.getSession(false);
        if (session == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No session");
            return;
        }

        var postLoginUrl = (String) session.getAttribute(SESSION_REDIRECT_KEY);
        if (postLoginUrl == null || postLoginUrl.trim().isEmpty()) {
            postLoginUrl = cfg.getAfterLoginUrl();
        }

        var error = req.getParameter("error");
        if (error != null) {
            var derivedError = "unknown";
            if ("access_denied".equals(error)) {
                derivedError = "oidc_access_denied";
            }
            resp.sendRedirect(resp.encodeRedirectURL(cfg.getOnErrorUrl() + "?from=" + postLoginUrl + "&error=" + derivedError));
            return;
        }

        var code = req.getParameter("code");
        var state = req.getParameter("state");
        var expectedState = (String) session.getAttribute(SESSION_STATE_KEY);

        if (code == null || state == null || !state.equals(expectedState)) {
            log.warn("Invalid callback parameters: code={}, state={}, expectedState={}", code != null, state, expectedState);
            invalidateOrWarn(session);
            resp.sendRedirect(resp.encodeRedirectURL(OidcAuthFilter.URL + "?from=" + postLoginUrl));
            return;
        }

        try {
            var redirectUri = cfg.getUrlBase() + URL + "?client_name=oidc";
            var profile = oidcService.exchangeCodeForProfile(code, redirectUri);

            session.setAttribute(SESSION_PROFILE_KEY, profile);
            session.removeAttribute(SESSION_STATE_KEY);
            session.removeAttribute(SESSION_REDIRECT_KEY);

            resp.sendRedirect(resp.encodeRedirectURL(postLoginUrl));

        } catch (Exception e) {
            log.warn("OIDC callback error", e);
            invalidateOrWarn(session);
            resp.sendRedirect(resp.encodeRedirectURL(OidcAuthFilter.URL + "?from=" + postLoginUrl));
        }
    }

    private static void invalidateOrWarn(HttpSession session) {
        try {
            session.invalidate();
        } catch (Exception e) {
            log.warn("Unable to invalidate the session", e);
        }
    }
}
