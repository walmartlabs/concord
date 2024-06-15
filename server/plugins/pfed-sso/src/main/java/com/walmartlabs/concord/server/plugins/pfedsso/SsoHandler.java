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

import com.walmartlabs.concord.server.boot.filters.AuthenticationHandler;
import org.apache.shiro.authc.AuthenticationToken;

import javax.inject.Inject;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SsoHandler implements AuthenticationHandler {

    private final SsoConfiguration cfg;
    private final JwtAuthenticator jwtAuthenticator;
    private final RedirectHelper redirectHelper;
    private final SsoClient ssoClient;

    private static final String FORM_URL_PATTERN = "/forms/.*";

    @Inject
    public SsoHandler(SsoConfiguration cfg, JwtAuthenticator jwtAuthenticator, RedirectHelper redirectHelper, SsoClient ssoClient) {
        this.cfg = cfg;
        this.jwtAuthenticator = jwtAuthenticator;
        this.redirectHelper = redirectHelper;
        this.ssoClient = ssoClient;
    }

    @Override
    public AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        if (!cfg.isEnabled()) {
            return null;
        }

        HttpServletRequest req = (HttpServletRequest) request;

        String bearerToken = cfg.getEnableBearerTokens() ? extractTokenFromRequest(req) : null;
        String token = bearerToken != null ? bearerToken : SsoCookies.getTokenCookie(req);

        if (token == null) {
            return null;
        }

        boolean restrictOnClientId = (bearerToken != null) && (!cfg.getAllowAllClientIds());

        if (!jwtAuthenticator.isTokenValid(token, restrictOnClientId)) {
            return null;
        }

        try {
            SsoClient.Profile profile = bearerToken != null ? ssoClient.getProfile(bearerToken) :
                    ssoClient.getUserProfileByRefreshToken(SsoCookies.getRefreshCookie(req));

            if (profile == null) {
                return null;
            }

            String[] as = parseDomain(profile.sub());

            return new SsoToken(as[0], as[1], profile.displayName(), profile.mail(), profile.userPrincipalName(), profile.nameInNamespace(), profile.groups());

        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean onAccessDenied(ServletRequest request, ServletResponse response) throws IOException {
        if (!cfg.isEnabled()) {
            return false;
        }

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String p = req.getRequestURI();
        if (p.matches(FORM_URL_PATTERN)) {
            redirectHelper.sendRedirect(resp, "/api/service/sso/auth?from=" + p);
            return true;
        }

        return false;
    }

    private String[] parseDomain(String s) {
        s = s.trim();

        int pos = s.indexOf("@");
        if (pos < 0) {
            return new String[]{s, null};
        }

        String username = s.substring(0, pos);
        String domain = s.substring(pos + 1);
        return new String[]{username, domain};
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        final String value = request.getHeader("Authorization");

        if (value == null || !value.toLowerCase().startsWith("bearer")) {
            return null;
        }

        String[] parts = value.split(" ");

        if (parts.length < 2) {
            return null;
        }

        return parts[1].trim();
    }
}
