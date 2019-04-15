package com.walmartlabs.concord.server.security.sso;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.server.cfg.SsoConfiguration;
import org.apache.shiro.web.util.WebUtils;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

public class SsoCallbackFilter implements Filter {

    private final SsoConfiguration cfg;
    private final SsoClient ssoClient;
    private final JwtAuthenticator jwtAuthenticator;

    @Inject
    public SsoCallbackFilter(SsoConfiguration cfg, SsoClient ssoClient, JwtAuthenticator jwtAuthenticator) {
        this.cfg = cfg;
        this.ssoClient = ssoClient;
        this.jwtAuthenticator = jwtAuthenticator;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException {
        HttpServletResponse resp = WebUtils.toHttp(response);
        HttpServletRequest req = WebUtils.toHttp(request);

        if (!cfg.isEnabled()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "sso disabled");
            return;
        }

        String code = req.getParameter("code");
        if (code == null || code.trim().isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "code param missing");
            return;
        }

        String state = req.getParameter("state");
        if (state == null || state.trim().isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "state param missing");
            return;
        }

        SsoClient.Token token = ssoClient.getToken(code, cfg.getRedirectUrl());

        if (cfg.isValidateNonce()) {
            HttpSession session = req.getSession();
            String nonce = (String) session.getAttribute("ssoNonce");
            boolean isValid = jwtAuthenticator.isTokenValid(token.idToken(), nonce);
            if (!isValid) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid nonce");
                return;
            }
        }

        SsoCookies.addTokenCookie(token.idToken(), token.expiresIn(), resp);


        String redirectUrl = SsoCookies.getFromCookie(req);
        if (redirectUrl == null || redirectUrl.trim().isEmpty()) {
            redirectUrl = "";
        }
        resp.sendRedirect(resp.encodeRedirectURL(redirectUrl));
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // do nothing
    }

    @Override
    public void destroy() {
        // do nothing
    }
}
