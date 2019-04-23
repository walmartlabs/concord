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

import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

public class SsoCallbackFilter extends AbstractHttpFilter {

    private final SsoConfiguration cfg;
    private final SsoClient ssoClient;
    private final JwtAuthenticator jwtAuthenticator;
    private final RedirectHelper redirectHelper;

    @Inject
    public SsoCallbackFilter(SsoConfiguration cfg, SsoConfiguration cfg1, SsoClient ssoClient,
                             JwtAuthenticator jwtAuthenticator, RedirectHelper redirectHelper) {
        this.cfg = cfg1;
        this.ssoClient = ssoClient;
        this.jwtAuthenticator = jwtAuthenticator;
        this.redirectHelper = redirectHelper;
    }

    @Override
    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException {
        if (!cfg.isEnabled()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "sso disabled");
            return;
        }

        String code = request.getParameter("code");
        if (code == null || code.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "code param missing");
            return;
        }

        String state = request.getParameter("state");
        if (state == null || state.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "state param missing");
            return;
        }

        SsoClient.Token token = ssoClient.getToken(code, cfg.getRedirectUrl());

        if (cfg.isValidateNonce()) {
            HttpSession session = request.getSession();
            String nonce = (String) session.getAttribute("ssoNonce");
            boolean isValid = jwtAuthenticator.isTokenValid(token.idToken(), nonce);
            if (!isValid) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid nonce");
                return;
            }
        }

        SsoCookies.addTokenCookie(token.idToken(), token.expiresIn(), response);

        String redirectUrl = SsoCookies.getFromCookie(request);
        if (redirectUrl == null || redirectUrl.trim().isEmpty()) {
            redirectUrl = "/";
        }
        redirectHelper.sendRedirect(response, redirectUrl);
    }
}
