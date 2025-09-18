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

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

// TODO can be implemented as a JAX-RS resource
public class OidcAuthFilter implements Filter {

    public static final String URL = "/api/service/oidc/auth";
    private static final String SESSION_STATE_KEY = "OIDC_STATE";
    private static final String SESSION_REDIRECT_KEY = "OIDC_REDIRECT_URL";

    private final PluginConfiguration pluginConfig;
    private final OidcService oidcService;

    @Inject
    public OidcAuthFilter(PluginConfiguration pluginConfig, OidcService oidcService) {
        this.pluginConfig = pluginConfig;
        this.oidcService = oidcService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException {
        var req = (HttpServletRequest) request;
        var resp = (HttpServletResponse) response;

        if (!pluginConfig.isEnabled()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "OIDC disabled");
            return;
        }

        var redirectUrl = req.getParameter("from");
        var state = UUID.randomUUID().toString();
        var callbackUrl = pluginConfig.getUrlBase() + OidcCallbackFilter.URL + "?client_name=oidc";

        var session = req.getSession(true);
        session.setAttribute(SESSION_STATE_KEY, state);
        session.setAttribute(SESSION_REDIRECT_KEY, redirectUrl);

        var authUrl = oidcService.buildAuthorizationUrl(callbackUrl, state);
        resp.sendRedirect(authUrl);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
