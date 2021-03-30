package com.walmartlabs.concord.server.plugins.pfedsso;

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


import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.walmartlabs.concord.server.plugins.pfedsso.SsoCookies.REFRESH_TOKEN_COOKIE;
import static com.walmartlabs.concord.server.plugins.pfedsso.SsoCookies.TOKEN_COOKIE;

public class SsoCallbackFilter extends AbstractHttpFilter {

    private final SsoConfiguration cfg;
    private final SsoClient ssoClient;
    private final RedirectHelper redirectHelper;

    @Inject
    public SsoCallbackFilter(SsoConfiguration cfg, SsoClient ssoClient, RedirectHelper redirectHelper) {
        this.cfg = cfg;
        this.ssoClient = ssoClient;
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

        SsoClient.Token token;
        token = ssoClient.getToken(code, cfg.getRedirectUrl());
        SsoCookies.addCookie(TOKEN_COOKIE, token.idToken(), token.expiresIn(), response);
        SsoCookies.addCookie(REFRESH_TOKEN_COOKIE, token.refreshToken(), token.expiresIn(), response);

        String redirectUrl = SsoCookies.getFromCookie(request);
        if (redirectUrl == null || redirectUrl.trim().isEmpty()) {
            redirectUrl = "/";
        }

        redirectHelper.sendRedirect(response, redirectUrl);
    }
}
