package com.walmartlabs.concord.server.plugins.iamsso;

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

import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SsoLogoutFilter extends AbstractHttpFilter {

    private final SsoConfiguration cfg;
    private final RedirectHelper redirectHelper;

    @Inject
    public SsoLogoutFilter(SsoConfiguration cfg, RedirectHelper redirectHelper) {
        this.cfg = cfg;
        this.redirectHelper = redirectHelper;
    }

    @Override
    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException {
        if (!cfg.isEnabled()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "SSO is disabled, the logout URL is not available");
            return;
        }

        String initParam = request.getParameter("init");
        boolean isInit = initParam != null && !initParam.trim().isEmpty();
        if (isInit) {
            redirectHelper.sendRedirect(response, cfg.getLogoutEndpointUrl());
            return;
        }

        SsoCookies.clear(response);
        Subject subject = SecurityUtils.getSubject();
        subject.logout();

        redirectHelper.sendRedirect(response, "/#/logout/done");
    }
}
