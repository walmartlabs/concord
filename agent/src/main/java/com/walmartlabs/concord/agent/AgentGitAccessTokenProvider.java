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

import com.walmartlabs.concord.agent.cfg.GitConfiguration;
import com.walmartlabs.concord.agent.remote.ApiClientFactory;
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.SystemApi;
import com.walmartlabs.concord.repository.auth.*;
import com.walmartlabs.concord.sdk.Secret;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

public class AgentGitAccessTokenProvider implements GitAccessTokenProvider {

    private final List<GitConfiguration.AuthConfig> authConfigs;
    private final ApiClient apiClient;

    public AgentGitAccessTokenProvider(List<GitConfiguration.AuthConfig> authConfigs,
                                       ApiClientFactory apiClientFactory) throws IOException {
        this.authConfigs = authConfigs;
        this.apiClient = apiClientFactory.create(null);
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
    public Optional<ActiveAccessToken> getAccessToken(String gitHost, URI repo, @Nullable Secret secret) {

        if (secret != null) {
            return fromSecret(repo, secret);
        }

        return authConfigs.stream()
                .filter(auth -> canHandle(auth.toGitAuth(), repo))
                .findFirst()
                .map(auth -> {
                    if (auth instanceof GitConfiguration.ConcordServerConfig) { // TODO do this as default/fallback? actually, maybe we enrich it with a different api token from system?
                        return fetchAccessToken(gitHost, repo);
                    }

                    if (auth instanceof GitConfiguration.OauthConfig oauth) {
                        return ActiveAccessToken.builder()
                                .token(oauth.token())
                                .build();
                    }

                    throw new IllegalArgumentException("Unsupported GitAuth type for repo: " + repo);
                });
    }

    private Optional<ActiveAccessToken> fromSecret(URI repo, @Nonnull Secret secret) {
        // TODO implement

        // determine type (token, app installation, ssh keypair)
        // if token or ssh keypair, return it..refactor ActiveAccessToken to support ssh keypair
        // if app installation, do the jwt dance (or maybe make the server handle/cache?)
        return Optional.empty();
    }

    ActiveAccessToken fetchAccessToken(String gitHost, URI repository) {
        var systemApi = new SystemApi(apiClient);

        try {
            var resp = systemApi.getSystemGitAuth(gitHost, repository);

            return ActiveAccessToken.builder()
                    .token(resp.getToken())
                    .expiresAt(resp.getExpiresAt())
                    .build();
        } catch (ApiException e) {
            throw new RuntimeException("Error retrieving system git auth", e);
        }
    }

}
