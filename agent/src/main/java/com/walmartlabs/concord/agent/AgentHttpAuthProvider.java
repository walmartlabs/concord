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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.walmartlabs.concord.agent.cfg.GitConfiguration;
import com.walmartlabs.concord.repository.auth.*;
import com.walmartlabs.concord.sdk.Secret;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;

public class AgentHttpAuthProvider implements HttpAuthProvider {

    private final List<GitConfiguration.AuthConfig> authConfigs;
    private final ObjectMapper objectMapper;

    public AgentHttpAuthProvider(List<GitConfiguration.AuthConfig> authConfigs,
                                 ObjectMapper objectMapper) {
        this.authConfigs = authConfigs;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean canHandle(URI gitHost) {
        return authConfigs.stream()
                .anyMatch(c -> c.gitHost().equals(gitHost));
    }

    private static boolean canHandle(GitAuth auth, URI repoUri) {
        return auth.baseUrl().getHost().equals(repoUri.getHost());
    }

    @Override
    public String get(String gitHost, URI repo, @Nullable Secret secret) {
        return authConfigs.stream()
                .filter(auth -> canHandle(auth.toGitAuth(), repo))
                .findFirst()
                .map(auth -> {
                    if (auth instanceof ConcordServer serverConfig) {
                        return fetchAccessToken(serverConfig, gitHost, repo);
                    }

                    throw new IllegalArgumentException("Unsupported GitAuth type for repo: " + repo);
                })
                .orElseGet(() -> {
                    if (secret != null) {
                        //TODO do we want to support regular secrets? I.e. username/password or keypair?
                        return null;
                    }
                    throw new IllegalArgumentException("No matching auth config or secret found for host: " + gitHost);
                });
    }

    String fetchAccessToken(ConcordServer serverConfig, String gitHost, URI repository) {
        var req = HttpRequest.newBuilder().GET()
                .uri(URI.create(serverConfig.concordServerHost() + "/" + serverConfig.tokenEndpointUrl() + "/"
                                + gitHost + "/" + repository))
                .header("Authorization", "Bearer " + "JWT") //TODO what auth do we use for the agent to server?
                .header("Accept", "application/json")
                .build();

        try {
            AgentAppInstallationAccessToken accessToken =
                    sendRequest(req, 201, AgentAppInstallationAccessToken.class);
            return accessToken.token();
        } catch (IOException e) {
            throw new RuntimeException("Error generating app access token", e);
        }
    }

    private <T> T sendRequest(HttpRequest httpRequest, int expectedCode, Class<T> clazz) throws IOException {
        try {
            var resp = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != expectedCode) {
                throw new RuntimeException("Failed to retrieve app installation info, status code: " + resp.statusCode());
            }
            return objectMapper.readValue(resp.body(), clazz);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        throw new IllegalStateException("Unexpected error sending HTTP request");
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    @JsonDeserialize(as = ImmutableAgentAppInstallationAccessToken.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public interface AgentAppInstallationAccessToken {

        String token();

        @JsonProperty("expires_at")
        OffsetDateTime expiresAt();

    }
}
