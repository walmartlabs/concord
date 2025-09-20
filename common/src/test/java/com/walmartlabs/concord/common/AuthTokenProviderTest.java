package com.walmartlabs.concord.common;

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

import com.walmartlabs.concord.common.cfg.ExternalTokenAuth;
import com.walmartlabs.concord.common.cfg.OauthTokenConfig;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import org.immutables.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthTokenProviderTest {

    private static final byte[] SECRET_BYTES = "abc123".getBytes(StandardCharsets.UTF_8);
    private static final String MOCK_TOKEN = "mock-token";
    private static final String MOCK_USERNAME = "mock-username";
    private static final String VALID_REPO = "https://github.local/owner/repo.git";

    @Mock
    BinaryDataSecret binaryDataSecret;

    @Mock
    UsernamePassword usernamePassword;

    @Mock
    ExternalTokenAuth.Oauth oauth;

    @Mock
    TestOauthTokenConfig oauthTokenConfig;

    @Test
    void testSingleOauth() {
        // the "old" config approach
        when(oauthTokenConfig.getOauthToken()).thenReturn(Optional.of(MOCK_TOKEN));
        when(oauthTokenConfig.getOauthUrlPattern()).thenReturn(Optional.of("github\\.local"));
        when(oauthTokenConfig.getOauthUsername()).thenReturn(Optional.of(MOCK_USERNAME));

        executeWithoutSecret(oauthTokenConfig);

        verify(oauthTokenConfig, times(1)).getOauthUrlPattern(); // retrieved once and stored
    }

    @Test
    void testSystemAuth() {
        when(oauth.canHandle(any())).thenCallRealMethod();
        when(oauth.urlPattern()).thenReturn(Pattern.compile("github\\.local"));
        when(oauth.token()).thenReturn(MOCK_TOKEN);
        when(oauth.username()).thenReturn(Optional.of(MOCK_USERNAME));

        var cfg = TestOauthTokenConfig.builder()
                .addSystemAuth(oauth)
                .build();

        executeWithoutSecret(cfg);

        verify(oauth, times(12)).canHandle(any());
    }

    void executeWithoutSecret(OauthTokenConfig cfg) {
        var provider = new AuthTokenProvider.OauthTokenProvider(cfg);

        assertTrue(provider.supports(URI.create("https://github.local/owner/repo.git"), null));
        assertTrue(provider.supports(URI.create("https://github.local/owner/repo"), null));
        assertTrue(provider.supports(URI.create("https://github.local/owner/repo/"), null));
        assertFalse(provider.supports(URI.create("https://elsewhere.local/owner/repo.git"), null));
        assertFalse(provider.supports(URI.create("https://elsewhere.local/owner/repo"), null));

        assertEquals(MOCK_TOKEN, provider.getToken(URI.create("https://github.local/owner/repo.git"), null).map(ExternalAuthToken::token).orElse(null));
        assertEquals(MOCK_TOKEN, provider.getToken(URI.create("https://github.local/owner/repo"), null).map(ExternalAuthToken::token).orElse(null));
        assertEquals(MOCK_TOKEN, provider.getToken(URI.create("https://github.local/owner/repo/"), null).map(ExternalAuthToken::token).orElse(null));
        assertFalse(provider.getToken(URI.create("https://elsewhere.local/owner/repo.git"), null).isPresent());
        assertFalse(provider.getToken(URI.create("https://elsewhere.local/owner/repo"), null).isPresent());

        var enriched = provider.addUserInfoToUri(URI.create("https://github.local/owner/repo.git"), null);
        assertEquals(MOCK_USERNAME + ":" + MOCK_TOKEN, enriched.getUserInfo());
        assertEquals("https://" + MOCK_USERNAME + ":" + MOCK_TOKEN + "@github.local/owner/repo.git", enriched.toString());
    }

    @Test
    void testUsernamePassword() {
        var cfg = TestOauthTokenConfig.builder().build();
        var provider = new AuthTokenProvider.OauthTokenProvider(cfg);

        assertFalse(provider.supports(URI.create(VALID_REPO), usernamePassword));
    }

    @Test
    void testWithSecret() {
        var cfg = TestOauthTokenConfig.builder()
                .addSystemAuth(oauth) // won't be used
                .build();

        executeWithSecret(cfg);
    }

    @Test
    void testWithSecretNoDefault() {
        var cfg = TestOauthTokenConfig.builder().build();

        executeWithSecret(cfg);
    }

    private void executeWithSecret(TestOauthTokenConfig cfg) {
        var provider = new AuthTokenProvider.OauthTokenProvider(cfg);

        when(binaryDataSecret.getData()).thenReturn(SECRET_BYTES);
        assertTrue(provider.supports(URI.create("https://github.local/owner/repo.git"), binaryDataSecret));

        verify(oauth, never()).token(); // prove it wasn't used
        verify(binaryDataSecret, times(1)).getData();
    }

    @Value.Immutable
    interface TestOauthTokenConfig extends OauthTokenConfig {
        static ImmutableTestOauthTokenConfig.Builder builder() {
            return ImmutableTestOauthTokenConfig.builder();
        }
    }
}
