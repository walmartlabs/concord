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
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.util.WebUtils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Named
public class SsoHandler {

    private final SsoConfiguration cfg;
    private final JwtAuthenticator jwtAuthenticator;
    private final RedirectHelper redirectHelper;

    private static final String FORM_URL_PATTERN = "/forms/.*";

    @Inject
    public SsoHandler(SsoConfiguration cfg, JwtAuthenticator jwtAuthenticator, RedirectHelper redirectHelper) {
        this.cfg = cfg;
        this.jwtAuthenticator = jwtAuthenticator;
        this.redirectHelper = redirectHelper;
    }

    public AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        if (!cfg.isEnabled()) {
            return null;
        }

        HttpServletRequest req = WebUtils.toHttp(request);

        String token = SsoCookies.getTokenCookie(req);
        if (token == null) {
            return null;
        }

        String login = jwtAuthenticator.validateTokenAndGetLogin(token);
        if (login == null) {
            return null;
        }
        return new SsoToken(login);
    }

    public boolean onAccessDenied(ServletRequest request, ServletResponse response) throws IOException {
        if (!cfg.isEnabled()) {
            return false;
        }

        HttpServletRequest req = WebUtils.toHttp(request);
        HttpServletResponse resp = WebUtils.toHttp(response);

        String p = req.getRequestURI();
        if (p.matches(FORM_URL_PATTERN)) {
            redirectHelper.sendRedirect(resp, "/api/service/sso/auth?from=" + p);
            return true;
        }

        return false;
    }
}
