package com.walmartlabs.concord.server.console3;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.server.security.UserPrincipal;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * If the request is authenticated provides the current {@link UserPrincipal}.
 */
public class UserPrincipalFilter extends HttpFilter {

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        if (ResteasyProviderFactory.getInstance().getContextData(UserPrincipal.class) != null) {
            chain.doFilter(req, res);
            return;
        }

        var principal = UserPrincipal.getCurrent();
        if (principal != null) {
            ResteasyContext.pushContext(UserPrincipal.class, principal);
        }

        chain.doFilter(req, res);
    }
}
