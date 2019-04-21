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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;

public class SsoAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SsoAuthFilter.class);

    private final SsoConfiguration cfg;

    private final JwtAuthenticator jwtAuthenticator;

    @Inject
    public SsoAuthFilter(SsoConfiguration cfg, JwtAuthenticator jwtAuthenticator) {
        this.cfg = cfg;
        this.jwtAuthenticator = jwtAuthenticator;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = WebUtils.toHttp(request);
        HttpServletResponse resp = WebUtils.toHttp(response);

        if (!cfg.isEnabled()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "sso disabled");
            return;
        }

        String from = request.getParameter("from");
        if (from == null || from.trim().isEmpty()) {
            from = "/";
        }

        String token = SsoCookies.getTokenCookie(req);
        if (token != null) {
            boolean isValid = jwtAuthenticator.isTokenValid(token);
            if (isValid) {
                log.info("doFilter -> found valid token in cookies, redirect to '{}'", from);
                resp.sendRedirect(resp.encodeRedirectURL(from));
                return;
            } else {
                SsoCookies.removeTokenCookie(resp);
            }
        }

        SsoCookies.addFromCookie(from, resp);

        String nonce = generateNonce();
        String state = generateState();
        if (cfg.isValidateNonce()) {
            HttpSession session = req.getSession();
            session.setAttribute("ssoNonce", nonce);
            session.setAttribute("ssoState", state);
        }
        resp.sendRedirect(resp.encodeRedirectURL(getAuthzUrl(nonce, state)));
    }

    private String getAuthzUrl(String nonce, String state) {
        return String.format("%s?response_type=code" +
                "&scope=openid" +
                "&client_id=%s" +
                "&redirect_uri=%s" +
                "&nonce=%s" +
                "&state=%s",
                cfg.getAuthEndPointUrl(), cfg.getClientId(), cfg.getRedirectUrl(), nonce, state);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // do nothing
    }

    @Override
    public void destroy() {
        // do nothing
    }

    private static String generateNonce() {
        return new BigInteger(50, ThreadLocalRandom.current()).toString(16);
    }

    private static String generateState() {
        return new BigInteger(50, ThreadLocalRandom.current()).toString(16);
    }
}
