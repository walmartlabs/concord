package com.walmartlabs.concord.server.security;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.boot.filters.ConcordAuthorizationFilter;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.org.project.EncryptedProjectValueManager;
import com.walmartlabs.concord.server.sdk.security.AuthorizationUnavailableException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GithubAuthenticatingFilterTest {
    @Test
    void requiresWebhookAuthenticationEvenWithExistingSessionAndPreservesFailure() {
        GithubConfiguration cfg = mock(GithubConfiguration.class);
        when(cfg.getSecret()).thenReturn("secret");
        var filter = new GithubAuthenticatingFilter(cfg, mock(EncryptedProjectValueManager.class), new ObjectMapper());
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        assertFalse(filter.isAccessAllowed(request, response, null));
        assertFalse(filter.onLoginFailure(new UsernamePasswordToken(), new AuthenticationException("failure",
                new AuthorizationUnavailableException("offline")), request, response));
        verify(request).setAttribute(ConcordAuthorizationFilter.AUTHORIZATION_FAILURE, 503);
    }
}
