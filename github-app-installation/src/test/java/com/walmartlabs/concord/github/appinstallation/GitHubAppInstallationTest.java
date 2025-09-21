package com.walmartlabs.concord.github.appinstallation;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ExternalAuthToken;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import com.walmartlabs.concord.common.cfg.ExternalTokenAuth;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.github.appinstallation.cfg.GitHubAppInstallationConfig;
import com.walmartlabs.concord.sdk.Secret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static com.walmartlabs.concord.github.appinstallation.TestConstants.APP_INSTALL_CONTENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubAppInstallationTest {

    private static final ObjectMapper MAPPER = new ObjectMapperProvider().get();

    @Mock
    AccessTokenProvider accessTokenProvider;

    private static final URI VALID_APP_AUTH_URI_01 = URI.create("https://github.local/owner/repo-1.git");
    private static final URI VALID_APP_AUTH_URI_02 = URI.create("https://github.local/owner/repo-2.git");
    private static final URI VALID_STATIC_AUTH_URI_01 = URI.create("https://staticgithub.local/owner/repo-1.git");

    private static final AppInstallationAuth auth = AppInstallationAuth.builder()
            .clientId("123")
            .urlPattern(ExternalTokenAuth.assertBaseUrlPattern("(?<baseUrl>github.local)/"))
            .privateKey("/does/not/exist")
            .build();
    private static final ExternalTokenAuth.Oauth staticKeyAuth = ExternalTokenAuth.Oauth.builder()
            .urlPattern(ExternalTokenAuth.assertBaseUrlPattern("(?<baseUrl>staticgithub.local)/"))
            .token("static-token")
            .build();
    private final GitHubAppInstallationConfig CFG = GitHubAppInstallationConfig.builder()
            .authConfigs(List.of(staticKeyAuth, auth))
            .build();
    private static final ExternalAuthToken TOKEN_1HR = ExternalAuthToken.SimpleToken.builder()
            .token("mock-installation-token")
            .expiresAt(OffsetDateTime.now().plusHours(1))
            .build();
    private static final ExternalAuthToken TOKEN_200S = ExternalAuthToken.SimpleToken.builder()
            .token("mock-200-seconds-expiration-token")
            .expiresAt(OffsetDateTime.now().plusSeconds(200))
            .build();
    private static final ExternalAuthToken TOKEN_9S = ExternalAuthToken.SimpleToken.builder()
            .token("mock-9-seconds-expiration-token")
            .expiresAt(OffsetDateTime.now().plusSeconds(9))
            .build();
    private static final Secret MOCK_STATIC_SECRET = new BinaryDataSecret(staticKeyAuth.token().getBytes(StandardCharsets.UTF_8));

    private static final Secret MOCK_APP_INSTALL_SECRET = new BinaryDataSecret(APP_INSTALL_CONTENT.getBytes(StandardCharsets.UTF_8));

    @Test
    void testCache() {
        // -- prepare 0
        var app = new TestApp(CFG, MAPPER, accessTokenProvider);

        // -- validate 0
        // empty when nothing was ever looked up
        assertEquals(0, app.cacheSize());

        // -- prepare 1
        when(accessTokenProvider.getRepoInstallationToken(any(), any()))
                .thenReturn(TOKEN_1HR);

        // -- execute 1
        var tokenResp = app.getToken(VALID_APP_AUTH_URI_01, null);

        // -- verify 1
        assertTrue(tokenResp.isPresent());
        assertEquals(TOKEN_1HR, tokenResp.get());
        assertEquals(1, app.cacheSize());
        verify(accessTokenProvider, times(1))
                .getRepoInstallationToken(any(), any());

        // -- execute 2
        // should hit cache
        app.getToken(VALID_APP_AUTH_URI_01, null);
        app.getToken(VALID_APP_AUTH_URI_01, null);

        // -- verify 2
        verify(accessTokenProvider, times(1))
                .getRepoInstallationToken(any(), any());

        // -- execute 3
        // Different repo, will retrieve first and hit cache second
        app.getToken(VALID_APP_AUTH_URI_02, null);
        app.getToken(VALID_APP_AUTH_URI_02, null);

        // -- verify 3
        // cache now has 2 entries
        verify(accessTokenProvider, times(2))
                .getRepoInstallationToken(any(), any());
    }

    @Test
    void testRefreshBeforeExpire() {
        // -- prepare 0
        when(accessTokenProvider.getRepoInstallationToken(any(), any()))
                .thenReturn(TOKEN_200S, TOKEN_1HR);
        var app = new TestApp(CFG, MAPPER, accessTokenProvider);

        // -- execute 1
        var tokenResp = app.getToken(VALID_APP_AUTH_URI_01, null);

        // -- verify 1
        // first is usable so we receive that token, new token is refreshed in background
        assertTrue(tokenResp.isPresent());
        assertEquals(TOKEN_200S, tokenResp.get());
        assertEquals(1, app.cacheSize());
        //
        verify(accessTokenProvider, times(2))
                .getRepoInstallationToken(any(), any());
    }

    @Test
    void testForceRefreshBeforeExpire() {
        // -- prepare 0
        when(accessTokenProvider.getRepoInstallationToken(any(), any()))
                .thenReturn(TOKEN_9S, TOKEN_1HR);
        var app = new TestApp(CFG, MAPPER, accessTokenProvider);

        // -- execute 1
        var tokenResp = app.getToken(VALID_APP_AUTH_URI_01, null);

        // -- verify 1
        assertTrue(tokenResp.isPresent());
        assertEquals(TOKEN_1HR, tokenResp.get());
        assertEquals(1, app.cacheSize());
        // first is usable refresh is forced we get the new token
        verify(accessTokenProvider, times(2))
                .getRepoInstallationToken(any(), any());
    }

    @Test
    void testFromStaticTokenTokenConfig() {
        // -- prepare 0
        var app = new TestApp(CFG, MAPPER, accessTokenProvider);

        // -- execute 1
        var tokenResp = app.getToken(VALID_STATIC_AUTH_URI_01, null);

        // -- verify 1
        assertTrue(tokenResp.isPresent());
        assertEquals(staticKeyAuth.token(), tokenResp.get().token());
        assertEquals(1, app.cacheSize());
        // not an app installation, no lookup expected
        verify(accessTokenProvider, times(0))
                .getRepoInstallationToken(any(), any());
    }

    @Test
    void testFromStaticTokenSecret() {
        // -- prepare 0
        var app = new TestApp(CFG, MAPPER, accessTokenProvider);

        // -- execute 1
        var tokenResp = app.getToken(VALID_APP_AUTH_URI_01, MOCK_STATIC_SECRET);

        // -- verify 1
        assertTrue(tokenResp.isPresent());
        assertEquals(staticKeyAuth.token(), tokenResp.get().token());
        assertEquals(1, app.cacheSize());
        // not an app installation, no lookup expected
        verify(accessTokenProvider, times(0))
                .getRepoInstallationToken(any(), any());
    }

    @Test
    void testFromAppInstallSecret() {
        // -- prepare 0
        when(accessTokenProvider.getRepoInstallationToken(any(), any()))
                .thenReturn(TOKEN_1HR);
        var app = new TestApp(CFG, MAPPER, accessTokenProvider);

        // -- execute 1
        var tokenResp = app.getToken(VALID_APP_AUTH_URI_01, MOCK_APP_INSTALL_SECRET);

        // -- verify 1
        assertTrue(tokenResp.isPresent());
        assertEquals(TOKEN_1HR, tokenResp.get());
        assertEquals(1, app.cacheSize());
        verify(accessTokenProvider, times(1))
                .getRepoInstallationToken(any(), any());
    }

    private static class TestApp extends GitHubAppInstallation {
        private final AccessTokenProvider tokenProvider;

        public TestApp(GitHubAppInstallationConfig cfg, ObjectMapper objectMapper, AccessTokenProvider tokenProvider) {
            super(cfg, objectMapper);
            this.tokenProvider = tokenProvider;
        }

        @Override
        AccessTokenProvider accessTokenProvider() {
            return tokenProvider;
        }
    }
}
