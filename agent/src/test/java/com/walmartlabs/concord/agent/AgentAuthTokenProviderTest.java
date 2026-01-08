package com.walmartlabs.concord.agent;

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

import com.walmartlabs.concord.common.AuthTokenProvider;
import com.walmartlabs.concord.common.ExternalAuthToken;
import com.walmartlabs.concord.github.appinstallation.GitHubAppInstallation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentAuthTokenProviderTest {

    @Mock
    GitHubAppInstallation ghApp;

    @Mock
    AuthTokenProvider.OauthTokenProvider oauthTokenProvider;

    @Test
    void testGitHubApp() {
        when(ghApp.getToken(any(), any())).
                thenReturn(Optional.of(ExternalAuthToken.SimpleToken.builder()
                        .token("gh-installation-token")
                        .expiresAt(OffsetDateTime.now().plusMinutes(60))
                        .build()));
        when(ghApp.supports(any(), any())).thenReturn(true);

        var provider = new AgentAuthTokenProvider(ghApp, oauthTokenProvider);

        // --

        assertTrue(provider.supports(URI.create("https://github.local/owner/repo.git"), null));
        var o = provider.getToken(URI.create("https://github.local/owner/repo.git"), null);

        // --

        assertTrue(o.isPresent());
        var result = assertInstanceOf(ExternalAuthToken.class, o.get());
        assertEquals("gh-installation-token", result.token());
    }

    @Test
    void testOauth() {
        when(oauthTokenProvider.supports(any(), any())).thenReturn(true);
        when(oauthTokenProvider.getToken(any(), any()))
                .thenReturn(Optional.of(ExternalAuthToken.StaticToken.builder()
                        .token("oauth-token")
                        .build()));

        var provider = new AgentAuthTokenProvider(ghApp, oauthTokenProvider);

        // --

        assertTrue(provider.supports(URI.create("https://github.local/owner/repo.git"), null));
        var o = provider.getToken(URI.create("https://github.local/owner/repo.git"), null);

        // --

        assertTrue(o.isPresent());
        var result = assertInstanceOf(ExternalAuthToken.class, o.get());
        assertEquals("oauth-token", result.token());
    }

    @Test
    void testNoAuth() {
        when(ghApp.supports(any(), any())).thenReturn(false);
        when(oauthTokenProvider.supports(any(), any())).thenReturn(false);

        var provider = new AgentAuthTokenProvider(ghApp, oauthTokenProvider);

        // --

        assertFalse(provider.supports(URI.create("https://github.local/owner/repo.git"), null));
        var o = provider.getToken(URI.create("https://github.local/owner/repo.git"), null);

        // --

        assertFalse(o.isPresent());
    }

}
