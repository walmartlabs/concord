package com.walmartlabs.concord.server.plugins.oidc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

public class OidcLogoutFilter implements Filter {

    public static final String URL = "/api/service/oidc/logout";

    private final PluginConfiguration cfg;

    @Inject
    public OidcLogoutFilter(PluginConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        var req = (HttpServletRequest) request;
        var resp = (HttpServletResponse) response;

        var session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        var afterLogoutUrl = Optional.ofNullable(req.getParameter("from")).orElse(cfg.getAfterLogoutUrl());
        resp.sendRedirect(afterLogoutUrl);
    }
}
