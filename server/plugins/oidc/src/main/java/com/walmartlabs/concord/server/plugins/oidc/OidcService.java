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
import java.time.Instant;

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

    public VerifiedProfile exchangeCodeForProfile(String code, String redirectUri) throws IOException {
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

        var tokenRequestedAt = Instant.now();
        try {
            var response = httpClient.send(request, BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Token exchange failed: " + response.statusCode());
            }

            var tokenResponse = objectMapper.readTree(response.body());
            var tokenNode = tokenResponse.get("access_token");
            if (tokenNode == null || !tokenNode.isTextual() || tokenNode.asText().isBlank()) {
                throw new IOException("No access token in response");
            }

            Instant tokenExpiry = null;
            var expiresIn = tokenResponse.get("expires_in");
            if (expiresIn != null) {
                if (!expiresIn.isIntegralNumber() || !expiresIn.canConvertToLong() || expiresIn.longValue() < 0) {
                    throw new IOException("Invalid token expiry in response");
                }
                try {
                    tokenExpiry = tokenRequestedAt.plusSeconds(expiresIn.longValue());
                } catch (RuntimeException e) {
                    throw new IOException("Invalid token expiry in response", e);
                }
            }
            return getUserInfo(tokenNode.asText(), discovery, tokenExpiry);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    public VerifiedProfile validateToken(String accessToken) throws IOException {
        return getUserInfo(accessToken, getDiscoveryDocument(), null);
    }

    private VerifiedProfile getUserInfo(String accessToken, DiscoveryDocument discovery, Instant tokenExpiry) throws IOException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(discovery.userinfoEndpoint()))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        try {
            var response = httpClient.send(request, BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("UserInfo request failed: " + response.statusCode());
            }

            var observedAt = Instant.now();
            var expiresAt = observedAt.plus(Duration.ofHours(1));
            if (tokenExpiry != null && tokenExpiry.isBefore(expiresAt)) {
                expiresAt = tokenExpiry;
            }
            if (!expiresAt.isAfter(observedAt)) {
                throw new IOException("Token expired before userinfo verification");
            }
            return new VerifiedProfile(UserProfileConverter.convert(objectMapper, response.body(), accessToken),
                    discovery.issuer(), observedAt, expiresAt);
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
            var issuer = discovery.path("issuer");
            if (!issuer.isTextual() || issuer.asText().isBlank()) {
                throw new IllegalStateException("OIDC discovery document has no issuer");
            }
            return new DiscoveryDocument(
                    issuer.asText(),
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
            String issuer,
            String authorizationEndpoint,
            String tokenEndpoint,
            String userinfoEndpoint
    ) {
    }
}
