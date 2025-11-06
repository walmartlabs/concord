package com.walmartlabs.concord.server.plugins.oidc;

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

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class OidcService {

    private final PluginConfiguration cfg;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile DiscoveryDocument discoveryDocument;

    @Inject
    public OidcService(PluginConfiguration cfg) {
        this.cfg = requireNonNull(cfg);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String buildAuthorizationUrl(String redirectUri, String state) {
        var discovery = getDiscoveryDocument();
        var scopes = cfg.getScopes() != null ? String.join(" ", cfg.getScopes()) : "openid email profile";
        return discovery.authorizationEndpoint() + "?" +
               "response_type=code" +
               "&client_id=" + urlEncode(cfg.getClientId()) +
               "&redirect_uri=" + urlEncode(redirectUri) +
               "&scope=" + urlEncode(scopes) +
               "&state=" + urlEncode(state);
    }

    public UserProfile exchangeCodeForProfile(String code, String redirectUri) throws IOException {
        var discovery = getDiscoveryDocument();

        var tokenRequest = "grant_type=authorization_code" +
                           "&code=" + urlEncode(code) +
                           "&redirect_uri=" + urlEncode(redirectUri) +
                           "&client_id=" + urlEncode(cfg.getClientId()) +
                           "&client_secret=" + urlEncode(cfg.getSecret());

        var request = HttpRequest.newBuilder()
                .uri(URI.create(discovery.tokenEndpoint()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(tokenRequest))
                .build();

        try {
            var response = httpClient.send(request, BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Token exchange failed: " + response.statusCode() + " " + response.body());
            }

            var tokenResponse = objectMapper.readTree(response.body());
            var accessToken = tokenResponse.get("access_token").asText();
            if (accessToken == null) {
                throw new IOException("No access token in response");
            }

            return getUserInfo(accessToken, discovery.userinfoEndpoint());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    public UserProfile validateToken(String accessToken) throws IOException {
        var discovery = getDiscoveryDocument();
        return getUserInfo(accessToken, discovery.userinfoEndpoint());
    }

    private UserProfile getUserInfo(String accessToken, String userinfoEndpoint) throws IOException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(userinfoEndpoint))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        try {
            var response = httpClient.send(request, BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("UserInfo request failed: " + response.statusCode() + " " + response.body());
            }

            var userInfo = objectMapper.readTree(response.body());
            var id = userInfo.get("sub").asText();
            var email = userInfo.get("email").asText();
            var displayName = userInfo.get("name").asText(email);
            return new UserProfile(id, email, displayName, accessToken, userInfo);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    private DiscoveryDocument getDiscoveryDocument() {
        if (discoveryDocument == null) {
            synchronized (this) {
                if (discoveryDocument == null) {
                    discoveryDocument = fetchDiscoveryDocument();
                }
            }
        }
        return discoveryDocument;
    }

    private DiscoveryDocument fetchDiscoveryDocument() {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getDiscoveryUri()))
                    .GET()
                    .build();

            var response = httpClient.send(request, BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Discovery document fetch failed: " + response.statusCode());
            }

            var discovery = objectMapper.readTree(response.body());
            return new DiscoveryDocument(
                    discovery.get("authorization_endpoint").asText(),
                    discovery.get("token_endpoint").asText(),
                    discovery.get("userinfo_endpoint").asText()
            );
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to fetch OIDC discovery document", e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, UTF_8);
    }

    private record DiscoveryDocument(
            String authorizationEndpoint,
            String tokenEndpoint,
            String userinfoEndpoint
    ) {
    }
}
