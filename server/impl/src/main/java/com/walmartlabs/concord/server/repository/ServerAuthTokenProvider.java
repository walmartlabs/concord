package com.walmartlabs.concord.server.repository;

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

import com.codahale.metrics.MetricRegistry;
import com.walmartlabs.concord.common.AuthTokenProvider;
import com.walmartlabs.concord.common.ExternalAuthToken;
import com.walmartlabs.concord.github.appinstallation.GitHubAppInstallation;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.net.URI;
import java.util.List;
import java.util.Optional;

@Named
@Singleton
public class ServerAuthTokenProvider implements AuthTokenProvider {

    private final List<AuthTokenProvider> authTokenProviders;

    @Inject
    public ServerAuthTokenProvider(GitHubAppInstallation githubProvider,
                                   OauthTokenProvider oauthTokenProvider,
                                   MetricRegistry metricRegistry) {
        this.authTokenProviders = List.of(githubProvider, oauthTokenProvider);

        metricRegistry.gauge("github-token-cache-size", () -> githubProvider::cacheSize);
    }

    @Override
    public boolean supports(URI repo, @Nullable Secret secret) {
        return authTokenProviders.stream()
                .anyMatch(p -> p.supports(repo, secret));
    }

    @WithTimer
    public Optional<ExternalAuthToken> getToken(URI repo, @Nullable Secret secret) {
        for (var tokenProvider : authTokenProviders) {
            if (tokenProvider.supports(repo, secret)) {
                return tokenProvider.getToken(repo, secret);
            }
        }

        return Optional.empty();
    }

}
