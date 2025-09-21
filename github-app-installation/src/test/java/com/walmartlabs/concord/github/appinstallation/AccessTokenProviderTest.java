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

import com.walmartlabs.concord.common.cfg.ExternalTokenAuth;
import com.walmartlabs.concord.github.appinstallation.cfg.GitHubAppInstallationConfig;
import com.walmartlabs.concord.github.appinstallation.exception.GitHubAppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;

import static com.walmartlabs.concord.github.appinstallation.TestConstants.PRIVATE_KEY_TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccessTokenProviderTest {

    @Mock
    HttpClient httpClient;

    @Mock
    HttpResponse<InputStream> tokenUrlResponse;

    @Mock
    HttpResponse<InputStream> accessTokenResponse;

    private static final AppInstallationAuth auth = AppInstallationAuth.builder()
            .urlPattern(ExternalTokenAuth.assertBaseUrlPattern("(?<baseUrl>github.local)/"))
            .privateKey(PRIVATE_KEY_TEXT)
            .clientId("123")
            .build();

    private static final GitHubAppInstallationConfig CFG = GitHubAppInstallationConfig.builder()
            .authConfigs(List.of(auth))
            .build();
    @Test
    void test() throws Exception {
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(tokenUrlResponse, accessTokenResponse);

        when(tokenUrlResponse.statusCode()).thenReturn(200);
        when(tokenUrlResponse.body()).thenReturn(asInputStream(ACCESS_TOKEN_URL_RESPONSE));

        when(accessTokenResponse.statusCode()).thenReturn(201);
        when(accessTokenResponse.body()).thenReturn(asInputStream(ACCESS_TOKEN_RESPONSE));

        var provider = new AccessTokenProvider(CFG, TestConstants.MAPPPER, () -> httpClient);

        // --

        var result = provider.getRepoInstallationToken(auth, "owner/repo");

        // --

        assertNotNull(result);
        assertEquals("mock-token", result.token());
        assertTrue(result.secondsUntilExpiration() > 300);
    }

    @Test
    void testAppNotInstalled() throws Exception {
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(tokenUrlResponse);

        when(tokenUrlResponse.statusCode()).thenReturn(404);
        when(tokenUrlResponse.body()).thenReturn(asInputStream("App is not installed on repo"));

        var provider = new AccessTokenProvider(CFG, TestConstants.MAPPPER, () -> httpClient);

        // --

        var ex = assertThrows(GitHubAppException.NotFoundException.class,
                () -> provider.getRepoInstallationToken(auth, "owenr/repo"));

        // --

        assertTrue(ex.getMessage().contains("Repo not found or App installation not found for repo"));
    }

    @Test
    void testErrorCreatingToken() throws Exception {
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(tokenUrlResponse, accessTokenResponse);

        when(tokenUrlResponse.statusCode()).thenReturn(200);
        when(tokenUrlResponse.body()).thenReturn(asInputStream(ACCESS_TOKEN_URL_RESPONSE));

        when(accessTokenResponse.statusCode()).thenReturn(500);
        when(accessTokenResponse.body()).thenReturn(asInputStream("server error"));

        var provider = new AccessTokenProvider(CFG, TestConstants.MAPPPER, () -> httpClient);

        // --

        var ex = assertThrows(GitHubAppException.class,
                () -> provider.getRepoInstallationToken(auth, "owenr/repo"));

        // --

        assertTrue(ex.getMessage().contains("Unexpected error creating app access token: 500"));
    }

    private static final String ACCESS_TOKEN_URL_RESPONSE = """
            {
                "access_tokens_url": "https://github.local/access_tokens"
            }""";

    private static final String ACCESS_TOKEN_RESPONSE = """
            {
                "token": "mock-token",
                "expires_at": "2099-12-31T23:59:59Z"
            }""";

    private static InputStream asInputStream(String s) {
        return new ByteArrayInputStream(s.getBytes());
    }

}
