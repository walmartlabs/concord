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
import org.apache.shiro.web.util.WebUtils;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.oidc.profile.OidcProfile;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@Named
@Singleton
public class OidcAuthenticationHandler implements AuthenticationHandler {

    private final PluginConfiguration cfg;

    @Inject
    public OidcAuthenticationHandler(PluginConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        if (!cfg.isEnabled()) {
            return null;
        }

        HttpServletRequest req = WebUtils.toHttp(request);
        HttpServletResponse resp = WebUtils.toHttp(response);

        JEEContext context = new JEEContext(req, resp);
        ProfileManager<OidcProfile> profileManager = new ProfileManager<>(context);

        Optional<OidcProfile> profile = profileManager.get(true);
        return profile.map(OidcToken::new).orElse(null);
    }

    @Override
    public boolean onAccessDenied(ServletRequest request, ServletResponse response) throws IOException {
        if (!cfg.isEnabled()) {
            return false;
        }

        HttpServletResponse resp = WebUtils.toHttp(response);
        resp.sendRedirect(OidcAuthFilter.URL);
        return true;
    }
}
