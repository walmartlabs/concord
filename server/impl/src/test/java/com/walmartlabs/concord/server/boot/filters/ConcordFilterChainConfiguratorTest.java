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

import com.walmartlabs.concord.server.security.GithubAuthenticatingFilter;
import org.apache.shiro.web.filter.mgt.FilterChainManager;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ConcordFilterChainConfiguratorTest {
    @Test
    void protectsBothAuthenticatedChainsWithoutChangingAnonymousChains() {
        var concord = mock(ConcordAuthenticatingFilter.class);
        var github = mock(GithubAuthenticatingFilter.class);
        var authorization = mock(ConcordAuthorizationFilter.class);
        var manager = mock(FilterChainManager.class);
        when(github.isReady()).thenReturn(true);
        new ConcordFilterChainConfigurator(concord, github, authorization).configure(manager);
        verify(manager).createChain("/api/**", "concord,currentAuthorization");
        verify(manager).createChain("/forms/**", "concord,currentAuthorization");
        verify(manager).createChain("/events/github/**", "github,currentAuthorization");
        verify(manager).createChain("/api/v1/server/ping", "anon");
        verify(manager).createChain("/api/v1/server/maintenance-mode", "local");
    }
}
