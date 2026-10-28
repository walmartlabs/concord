package com.walmartlabs.concord.server.boot.filters;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.security.AuthorizationFailure;
import com.walmartlabs.concord.server.security.AuthorizationScope;
import com.walmartlabs.concord.server.security.PrincipalNormalization;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.servlet.OncePerRequestFilter;

import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Prepares current grants even when the endpoint performs no permission check. */
public class ConcordAuthorizationFilter extends OncePerRequestFilter {
    public static final String NEW_AUTHENTICATION = ConcordAuthorizationFilter.class.getName() + ".newAuthentication";
    public static final String AUTHORIZATION_FAILURE = ConcordAuthorizationFilter.class.getName() + ".authorizationFailure";
    private final AuthorizationService authorizationService;

    @Inject
    public ConcordAuthorizationFilter(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    protected void doFilterInternal(ServletRequest request, ServletResponse response, FilterChain chain) throws ServletException, IOException {
        Subject original = org.apache.shiro.SecurityUtils.getSubject();
        AuthorizationScope scope = null;
        try {
            SubjectPrincipal principal;
            try {
                principal = PrincipalNormalization.find(original.getPrincipals());
            } catch (IllegalArgumentException conflict) {
                ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_FORBIDDEN);
                request.removeAttribute(NEW_AUTHENTICATION);
                request.removeAttribute(AUTHORIZATION_FAILURE);
                return;
            }
            if (principal != null) {
                scope = AuthorizationScope.open(authorizationService, principal,
                        Boolean.TRUE.equals(request.getAttribute(NEW_AUTHENTICATION)));
            }
        } catch (RuntimeException e) {
            ThreadContext.bind(original);
            request.removeAttribute(NEW_AUTHENTICATION);
            request.removeAttribute(AUTHORIZATION_FAILURE);
            int status = AuthorizationFailure.status(e);
            if (status == 0 && e instanceof com.walmartlabs.concord.server.sdk.security.AuthenticationException) {
                status = HttpServletResponse.SC_FORBIDDEN;
            }
            if (status == 0) throw e;
            ((HttpServletResponse) response).setStatus(status);
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (scope != null) scope.close();
            ThreadContext.bind(original);
            request.removeAttribute(NEW_AUTHENTICATION);
            request.removeAttribute(AUTHORIZATION_FAILURE);
        }
    }
}
