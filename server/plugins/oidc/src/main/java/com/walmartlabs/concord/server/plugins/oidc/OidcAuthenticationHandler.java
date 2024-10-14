package com.walmartlabs.concord.server.plugins.oidc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.credentials.TokenCredentials;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.oidc.config.OidcConfiguration;
import org.pac4j.oidc.credentials.authenticator.UserInfoOidcAuthenticator;
import org.pac4j.oidc.profile.OidcProfile;

import javax.inject.Inject;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

public class OidcAuthenticationHandler implements AuthenticationHandler {

    private static final String FORM_URL_PATTERN = "/forms/.*";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String HEADER_PREFIX = "Bearer";

    private final PluginConfiguration cfg;
    private final OidcConfiguration oidcCfg;

    @Inject
    public OidcAuthenticationHandler(PluginConfiguration cfg, OidcConfiguration oidcCfg) {
        this.cfg = cfg;
        this.oidcCfg = oidcCfg;
    }

    @Override
    public AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        if (!cfg.isEnabled()) {
            return null;
        }

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        JEEContext context = new JEEContext(req, resp);

        Optional<OidcProfile> profile;

        // check the token first
        String header = req.getHeader(AUTHORIZATION_HEADER);
        if (header != null) {
            String[] as = header.split(" ");
            if (as.length != 2 || !HEADER_PREFIX.equals(as[0])) {
                return null;
            }

            TokenCredentials credentials = new TokenCredentials(as[1].trim());

            UserInfoOidcAuthenticator authenticator = new UserInfoOidcAuthenticator(oidcCfg);
            authenticator.validate(credentials, context);

            // we know that UserInfoOidcAuthenticator produces OidcProfile, so we can cast to it here
            profile = Optional.ofNullable((OidcProfile) credentials.getUserProfile());
        } else {
            ProfileManager<OidcProfile> profileManager = new ProfileManager<>(context);
            profile = profileManager.get(true);
        }

        return profile.map(OidcToken::new).orElse(null);
    }

    @Override
    public boolean onAccessDenied(ServletRequest request, ServletResponse response) throws IOException {
        if (!cfg.isEnabled()) {
            return false;
        }

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (req.getRequestURI().matches(FORM_URL_PATTERN)) {
            resp.sendRedirect(resp.encodeRedirectURL(OidcAuthFilter.URL + "?from=" + req.getRequestURL()));
            return true;
        }

        return false;
    }
}
