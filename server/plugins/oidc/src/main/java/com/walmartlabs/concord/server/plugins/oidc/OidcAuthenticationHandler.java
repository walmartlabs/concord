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

import com.walmartlabs.concord.server.boot.filters.AuthenticationHandler;
import org.apache.shiro.authc.AuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class OidcAuthenticationHandler implements AuthenticationHandler {

    private static final Logger log = LoggerFactory.getLogger(OidcAuthenticationHandler.class);
    private static final String FORM_URL_PATTERN = "/forms/.*";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String HEADER_PREFIX = "Bearer";
    private static final String SESSION_PROFILE_KEY = "OIDC_USER_PROFILE";

    private final PluginConfiguration cfg;
    private final OidcService oidcService;

    @Inject
    public OidcAuthenticationHandler(PluginConfiguration cfg, OidcService oidcService) {
        this.cfg = cfg;
        this.oidcService = oidcService;
    }

    @Override
    public AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        if (!cfg.isEnabled()) {
            return null;
        }

        var req = (HttpServletRequest) request;

        var header = req.getHeader(AUTHORIZATION_HEADER);
        if (header == null) {
            var session = req.getSession(false);
            if (session == null) {
                return null;
            }

            var profile = (UserProfile) session.getAttribute(SESSION_PROFILE_KEY);
            return new OidcToken(profile);
        }

        var as = header.split(" ");
        if (as.length != 2 || !as[0].equals(HEADER_PREFIX)) {
            return null;
        }

        var accessToken = as[1].trim();
        try {
            var profile = oidcService.validateToken(accessToken);
            return new OidcToken(profile);
        } catch (IOException e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean onAccessDenied(ServletRequest request, ServletResponse response) throws IOException {
        if (!cfg.isEnabled()) {
            return false;
        }

        var req = (HttpServletRequest) request;
        var resp = (HttpServletResponse) response;

        if (req.getRequestURI().matches(FORM_URL_PATTERN)) {
            resp.sendRedirect(resp.encodeRedirectURL(OidcAuthFilter.URL + "?from=" + req.getRequestURL()));
            return true;
        }

        return false;
    }
}
