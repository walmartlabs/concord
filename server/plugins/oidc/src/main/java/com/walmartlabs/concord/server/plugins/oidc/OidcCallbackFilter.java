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

import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.web.util.WebUtils;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.engine.CallbackLogic;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.util.Pac4jConstants;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class OidcCallbackFilter implements Filter {

    public static final String URL = "/api/service/oidc/callback";

    private final PluginConfiguration cfg;
    private final Config pac4jConfig;

    @Inject
    public OidcCallbackFilter(PluginConfiguration cfg,
                              @Named("oidc") Config pac4jConfig) {

        this.cfg = cfg;
        this.pac4jConfig = pac4jConfig;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException {
        HttpServletRequest req = WebUtils.toHttp(request);
        HttpServletResponse resp = WebUtils.toHttp(response);

        if (!cfg.isEnabled()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "OIDC disabled");
            return;
        }

        JEEContext context = new JEEContext(req, resp, pac4jConfig.getSessionStore());

        String postLoginUrl = removeRequestedUrl(context);
        if (postLoginUrl == null || postLoginUrl.trim().isEmpty()) {
            postLoginUrl = cfg.getAfterLoginUrl();
        }

        try {
            CallbackLogic<?, JEEContext> callback = pac4jConfig.getCallbackLogic();
            callback.perform(context, pac4jConfig, pac4jConfig.getHttpActionAdapter(), postLoginUrl, true, false, true, OidcPluginModule.CLIENT_NAME);
        } catch (TechnicalException e) {
            throw new AuthorizationException("OIDC callback error: " + e.getMessage());
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // do nothing
    }

    @Override
    public void destroy() {
        // do nothing
    }

    @SuppressWarnings("unchecked")
    private static String removeRequestedUrl(JEEContext context) {
        SessionStore<JEEContext> sessionStore = context.getSessionStore();
        Object result = sessionStore.get(context, Pac4jConstants.REQUESTED_URL).orElse(null);
        sessionStore.set(context, Pac4jConstants.REQUESTED_URL, "");
        if (result instanceof String) {
            return (String) result;
        }
        return null;
    }
}
