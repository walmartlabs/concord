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

import com.codahale.metrics.Meter;
import com.walmartlabs.concord.server.sdk.security.AuthorizationSubjectNotFoundException;
import com.walmartlabs.concord.server.sdk.security.AuthorizationUnavailableException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Set;

import static org.mockito.Mockito.*;

class ConcordAuthenticatingFilterTest {
    @Test
    void classifiesProvisioningFailuresButNotBadCredentials() {
        var filter = new ConcordAuthenticatingFilter(Set.of(), mock(Meter.class), mock(Meter.class));
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var token = new UsernamePasswordToken("alice", "bad");
        filter.onLoginFailure(token, new AuthenticationException("failed", new AuthorizationUnavailableException("offline")), request, response);
        verify(request).setAttribute(ConcordAuthorizationFilter.AUTHORIZATION_FAILURE, 503);
        filter.onLoginFailure(token, new AuthenticationException("failed", new AuthorizationSubjectNotFoundException("missing")), request, response);
        verify(request).setAttribute(ConcordAuthorizationFilter.AUTHORIZATION_FAILURE, 403);
        reset(request);
        filter.onLoginFailure(token, new AuthenticationException("bad credentials"), request, response);
        verify(request, never()).setAttribute(eq(ConcordAuthorizationFilter.AUTHORIZATION_FAILURE), any());
    }
}
