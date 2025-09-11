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
import com.walmartlabs.concord.agent.cfg.ServerConfiguration;
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
    private final ServerConfiguration serverCfg;

    public AgentHttpAuthProvider(List<GitConfiguration.AuthConfig> authConfigs,
                                 ObjectMapper objectMapper, ServerConfiguration serverCfg) {
        this.authConfigs = authConfigs;
        this.objectMapper = objectMapper;
        this.serverCfg = serverCfg;
    }

    @Override
    public boolean canHandle(URI repoUri) {
        return authConfigs.stream()
                .anyMatch(c -> c.gitHost().equals(repoUri.getHost()));
    }

    private static boolean canHandle(GitAuth auth, URI repoUri) {
        return auth.baseUrl().equals(repoUri.getHost());
    }

    @Override
    public ActiveAccessToken get(String gitHost, URI repo, @Nullable Secret secret) {
        return authConfigs.stream()
                .filter(auth -> canHandle(auth.toGitAuth(), repo))
                .findFirst()
                .map(auth -> {
                    if (auth instanceof GitConfiguration.ConcordServerConfig concordserver) {
                        return fetchAccessToken(concordserver, gitHost, repo);
                    }

                    throw new IllegalArgumentException("Unsupported GitAuth type for repo: " + repo);
                })
                .orElse(null); // TODO as long as we support git.oauth we wont throw an exception here
    }

    ActiveAccessToken fetchAccessToken(GitConfiguration.ConcordServerConfig serverConfig, String gitHost, URI repository) {
        if(serverCfg.getApiKey() == null) {
            throw new IllegalStateException("Agent apiKey is required to retrieve app installation token");
        }
        var req = HttpRequest.newBuilder().GET()
                .uri(URI.create(serverCfg.getApiBaseUrl() + "/api/v1/secret/gitauth/token" +
                        "?gitHost=" + gitHost +
                        "&repoUri=" + repository.toString()
                ))
                .header("Authorization", serverCfg.getApiKey())
                .header("Accept", "application/json")
                .build();

        try {
            return sendRequest(req, 200, ActiveAccessToken.class);
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
