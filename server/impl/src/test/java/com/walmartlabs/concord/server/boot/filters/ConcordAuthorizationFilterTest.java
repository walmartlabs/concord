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

import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.AuthorizationSubjectNotFoundException;
import com.walmartlabs.concord.server.sdk.security.AuthorizationUnavailableException;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.security.AuthorizationScope;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConcordAuthorizationFilterTest {
    private final AuthorizationService service = mock(AuthorizationService.class);
    private final ConcordAuthorizationFilter filter = new ConcordAuthorizationFilter(service);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final UUID id = UUID.randomUUID();

    private void bindSubject() {
        Subject subject = mock(Subject.class);
        SubjectPrincipal principal = new SubjectPrincipal(id, "alice", null, new AuthenticationContext("oidc", null, null));
        when(subject.getPrincipals()).thenReturn(new SimplePrincipalCollection(principal, "oidc"));
        ThreadContext.bind(subject);
    }

    @AfterEach
    void cleanup() { ThreadContext.remove(); }

    @Test
    void preparesNewAndExistingSessionsAndClearsScopeOnFailure() throws Exception {
        bindSubject();
        when(request.getAttribute(ConcordAuthorizationFilter.NEW_AUTHENTICATION)).thenReturn(true, false);
        var grants = new AuthorizationService.EffectiveAuthorization(Set.of("role"), Set.of());
        when(service.prepare(id, true)).thenReturn(grants);
        when(service.prepare(id, false)).thenReturn(grants);
        doAnswer(invocation -> {
            assertSame(grants, AuthorizationScope.current(id));
            throw new ServletException("downstream");
        }).when(chain).doFilter(request, response);
        assertThrows(ServletException.class, () -> filter.doFilterInternal(request, response, chain));
        assertNull(AuthorizationScope.current(id));
        assertThrows(ServletException.class, () -> filter.doFilterInternal(request, response, chain));
        assertNull(AuthorizationScope.current(id));
        verify(service).prepare(id, true);
        verify(service).prepare(id, false);
    }

    @Test
    void preventsWebhookOrOtherHandlerFromRunningOnRequiredSourceFailures() throws Exception {
        bindSubject();
        when(service.prepare(id, false)).thenThrow(new AuthorizationUnavailableException("offline"))
                .thenThrow(new AuthorizationSubjectNotFoundException("missing"));
        filter.doFilterInternal(request, response, chain);
        verify(response).setStatus(503);
        filter.doFilterInternal(request, response, chain);
        verify(response).setStatus(403);
        verifyNoInteractions(chain);
        assertNull(AuthorizationScope.current(id));
    }

    @Test
    void disabledAccountDoesNotReachHandler() throws Exception {
        bindSubject();
        when(service.prepare(id, false)).thenThrow(new com.walmartlabs.concord.server.sdk.security.AuthenticationException("disabled"));
        filter.doFilterInternal(request, response, chain);
        verify(response).setStatus(403);
        verifyNoInteractions(chain);
    }

}
