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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;

public class SsoAuthFilter extends AbstractHttpFilter {

    private static final Logger log = LoggerFactory.getLogger(SsoAuthFilter.class);

    private final SsoConfiguration cfg;
    private final JwtAuthenticator jwtAuthenticator;
    private final RedirectHelper redirectHelper;

    @Inject
    public SsoAuthFilter(SsoConfiguration cfg, JwtAuthenticator jwtAuthenticator, RedirectHelper redirectHelper) {
        this.cfg = cfg;
        this.jwtAuthenticator = jwtAuthenticator;
        this.redirectHelper = redirectHelper;
    }

    @Override
    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException {
        if (!cfg.isEnabled()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "sso disabled");
            return;
        }

        String from = request.getParameter("from");
        if (from == null || from.trim().isEmpty()) {
            from = "/";
        }
        
        // do not redirect if refresh token is not null as it is invalid after one use. Unfortunately no way to validate RefreshToken
        // TODO: self-refresh using refresh token
        String token = SsoCookies.getTokenCookie(request);
        String refreshToken = SsoCookies.getRefreshCookie(request);
        
        if (token != null) {
            if (refreshToken == null){
                boolean isValid = jwtAuthenticator.isTokenValid(token, false);
                if (isValid) {
                    log.info("doFilter -> found valid token in cookies, redirect to '{}'", from);
                    redirectHelper.sendRedirect(response, from);
                    return;
                }
            }
            SsoCookies.removeTokenCookie(response);
        }

        if (refreshToken != null) {
            SsoCookies.removeRefreshTokenCookie(response);
        }

        SsoCookies.addFromCookie(from, response);

        String nonce = generateNonce();
        String state = generateState();
        if (cfg.isValidateNonce()) {
            HttpSession session = request.getSession();
            session.setAttribute("ssoNonce", nonce);
            session.setAttribute("ssoState", state);
        }

        redirectHelper.sendRedirect(response, getAuthzUrl(nonce, state));
    }

    private String getAuthzUrl(String nonce, String state) {
        return String.format("%s?response_type=code" +
                        "&scope=openid+profile+full" +
                        "&client_id=%s" +
                        "&redirect_uri=%s" +
                        "&nonce=%s" +
                        "&state=%s",
                cfg.getAuthEndPointUrl(), cfg.getClientId(), cfg.getRedirectUrl(), nonce, state);
    }

    private static String generateNonce() {
        return new BigInteger(50, ThreadLocalRandom.current()).toString(16);
    }

    private static String generateState() {
        return new BigInteger(50, ThreadLocalRandom.current()).toString(16);
    }
}
