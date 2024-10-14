package com.walmartlabs.concord.server.plugins.oidc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2020 Ivan Bodrov
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

import org.pac4j.core.config.Config;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.exception.http.RedirectionAction;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.oidc.client.OidcClient;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class OidcAuthFilter implements Filter {

    public static final String URL = "/api/service/oidc/auth";

    private final PluginConfiguration pluginConfig;
    private final Config oidcConfig;
    private final OidcClient<?> client;

    @Inject
    public OidcAuthFilter(PluginConfiguration pluginConfig, @Named("oidc") Config oidcConfig, OidcClient<?> client) {
        this.pluginConfig = pluginConfig;
        this.oidcConfig = oidcConfig;
        this.client = client;

        if (pluginConfig.isEnabled() && !client.isInitialized()) {
            client.init();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (!pluginConfig.isEnabled()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "OIDC disabled");
            return;
        }

        JEEContext context = new JEEContext(req, resp);

        String redirectUrl = req.getParameter("from");
        context.getSessionStore().set(context, Pac4jConstants.REQUESTED_URL, redirectUrl);

        RedirectionAction action = client.getRedirectionActionBuilder()
                .getRedirectionAction(context)
                .orElseThrow(() -> new IllegalStateException("Can't get a redirection action for the request"));

        oidcConfig.getHttpActionAdapter().adapt(action, context);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
