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
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.engine.CallbackLogic;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.util.Pac4jConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

public class OidcCallbackFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(OidcCallbackFilter.class);

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
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (!cfg.isEnabled()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "OIDC disabled");
            return;
        }

        JEEContext context = new JEEContext(req, resp, pac4jConfig.getSessionStore());

        String postLoginUrl = removeRequestedUrl(context);
        if (postLoginUrl == null || postLoginUrl.trim().isEmpty()) {
            postLoginUrl = cfg.getAfterLoginUrl();
        }

        String error = req.getParameter("error");
        if (error != null) {
            String derivedError = "unknown";
            if ("access_denied".equals(error)) {
                derivedError = "oidc_access_denied";
            }
            resp.sendRedirect(resp.encodeRedirectURL(cfg.getOnErrorUrl() + "?from=" + postLoginUrl + "&error=" + derivedError));
            return;
        }

        try {
            CallbackLogic<?, JEEContext> callback = pac4jConfig.getCallbackLogic();
            callback.perform(context, pac4jConfig, pac4jConfig.getHttpActionAdapter(), postLoginUrl, true, false, true, OidcPluginModule.CLIENT_NAME);
        } catch (TechnicalException e) {
            log.warn("OIDC callback error: {}", e.getMessage());
            HttpSession session = req.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            resp.sendRedirect(resp.encodeRedirectURL(OidcAuthFilter.URL + "?from=" + postLoginUrl));
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
