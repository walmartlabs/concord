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

import com.typesafe.config.ConfigFactory;
import com.walmartlabs.concord.agent.remote.ApiClientFactory;
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.common.ExternalAuthToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConcordServerTokenProviderTest {

    @Mock
    ApiClientFactory acf;

    @Mock
    HttpClient httpClient;

    @Mock
    HttpResponse httpResponse;

    @Test
    void testEnabled() throws Exception {
        when(acf.create(null)).thenReturn(new ApiClient(httpClient));
        when(httpClient.send(any(), any())).thenReturn(httpResponse);

        new ApiClient(httpClient);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
        when(httpResponse.body()).thenReturn(new ByteArrayInputStream("""
                {
                    "token": "token-from-concord",
                    "expires_at": "2099-12-31T23:59:59.123Z"
                }""".getBytes()));

        var config = ConfigFactory.parseString("""
                {
                    "externalTokenProvider" {
                        "enabled" = true,
                        "urlPattern" = "github.local"
                    }
                }""");

        var provider = new AgentAuthTokenProvider.ConcordServerTokenProvider(acf, config);

        // --

        assertFalse(provider.supports(URI.create("https://another.local/owner/repo.git"), null));
        assertTrue(provider.supports(URI.create("https://github.local/owner/repo.git"), null));

        var o = provider.getToken(URI.create("https://github.local/owner/repo.git"), null);

        // --

        assertTrue(o.isPresent());
        var result = assertInstanceOf(ExternalAuthToken.StaticToken.class, o.get());
        assertEquals("token-from-concord", result.token());
    }
    @Test
    void testNoPermission() throws Exception {
        when(acf.create(null)).thenReturn(new ApiClient(httpClient));
        when(httpClient.send(any(), any())).thenReturn(httpResponse);

        new ApiClient(httpClient);

        when(httpResponse.statusCode()).thenReturn(403);
        when(httpResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
        when(httpResponse.body()).thenReturn(new ByteArrayInputStream("not enough permission".getBytes()));

        var config = ConfigFactory.parseString("""
                {
                    "externalTokenProvider" {
                        "enabled" = true,
                        "urlPattern" = "github.local"
                    }
                }""");

        var provider = new AgentAuthTokenProvider.ConcordServerTokenProvider(acf, config);

        assertFalse(provider.supports(URI.create("https://another.local/owner/repo.git"), null));
        assertTrue(provider.supports(URI.create("https://github.local/owner/repo.git"), null));

        // --

        var ex = assertThrows(RuntimeException.class, () -> provider.getToken(URI.create("https://github.local/owner/repo.git"), null));

        // --

        assertTrue(ex.getMessage().contains("No permission to get auth token from concord server"));
    }

    @Test
    void testDisabled() throws Exception {
        when(acf.create(null)).thenReturn(new ApiClient(httpClient));

        var config = ConfigFactory.parseString("""
                {
                    "externalTokenProvider" {
                        "enabled" = false,
                        "urlPattern" = "github.local"
                    }
                }""");

        var provider = new AgentAuthTokenProvider.ConcordServerTokenProvider(acf, config);

        // --

        assertFalse(provider.supports(URI.create("https://another.local/owner/repo.git"), null));
        assertFalse(provider.supports(URI.create("https://github.local/owner/repo.git"), null));
    }
}
