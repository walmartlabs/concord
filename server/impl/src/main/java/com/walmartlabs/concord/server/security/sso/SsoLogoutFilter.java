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
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.util.WebUtils;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SsoLogoutFilter implements Filter {

    private final SsoConfiguration cfg;

    @Inject
    public SsoLogoutFilter(SsoConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException {
        HttpServletResponse resp = WebUtils.toHttp(response);
        HttpServletRequest req = WebUtils.toHttp(request);

        if (!cfg.isEnabled()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "sso disabled");
            return;
        }

        String initParam = req.getParameter("init");
        boolean isInit = initParam != null && !initParam.trim().isEmpty();
        if (isInit) {
            resp.sendRedirect(resp.encodeRedirectURL(cfg.getLogoutEndpointUrl()));
            return;
        }

        SsoCookies.clear(resp);
        Subject subject = SecurityUtils.getSubject();
        subject.logout();
        resp.sendRedirect(resp.encodeRedirectURL("/#/logout/done"));
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
