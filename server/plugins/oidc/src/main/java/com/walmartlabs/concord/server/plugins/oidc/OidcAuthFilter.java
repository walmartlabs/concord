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

    private final Config config;
    private final OidcClient<?> client;

    @Inject
    public OidcAuthFilter(@Named("oidc") Config config, OidcClient<?> client) {
        this.config = config;
        this.client = client;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        JEEContext context = new JEEContext(req, resp);

        String redirectUrl = req.getParameter("from");
        context.getSessionStore().set(context, Pac4jConstants.REQUESTED_URL, redirectUrl);

        RedirectionAction action = client.getRedirectionAction(context)
                .orElseThrow(() -> new IllegalStateException("Can't get a redirection action for the request"));

        config.getHttpActionAdapter().adapt(action, context);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
