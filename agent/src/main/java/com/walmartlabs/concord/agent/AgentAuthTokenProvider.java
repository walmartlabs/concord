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

import com.typesafe.config.Config;
import com.walmartlabs.concord.agent.remote.ApiClientFactory;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.SystemApi;
import com.walmartlabs.concord.common.AuthTokenProvider;
import com.walmartlabs.concord.common.ExpiringToken;
import com.walmartlabs.concord.common.cfg.ExternalTokenAuth;
import com.walmartlabs.concord.github.appinstallation.GitHubAppInstallation;
import com.walmartlabs.concord.sdk.Secret;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class AgentAuthTokenProvider implements AuthTokenProvider {

    private final List<AuthTokenProvider> authTokenProviders;

    @Inject
    public AgentAuthTokenProvider(ConcordServerTokenProvider concordProvider,
                                  GitHubAppInstallation githubProvider,
                                  AuthTokenProvider.OauthTokenProvider oauthTokenProvider) {

        this.authTokenProviders = List.of(
                concordProvider,
                githubProvider,
                oauthTokenProvider
        );
    }

    @Override
    public boolean supports(URI repo, @Nullable Secret secret) {
        return authTokenProviders.stream()
                .anyMatch(p -> p.supports(repo, secret));
    }

    public Optional<ExpiringToken> getToken(URI repo, @Nullable Secret secret) {
        for (var k : authTokenProviders) {
            if (k.supports(repo, secret)) {
                return k.getToken(repo, secret);
            }
        }

        return Optional.empty();
    }

    public static class ConcordServerTokenProvider implements AuthTokenProvider {

        private static final String CFG_ENABLED = "externalTokenProvider.enabled";
        private static final String CFG_URL_PATTERN = "externalTokenProvider.urlPattern";

        private final SystemApi systemApi;
        private final ExternalTokenAuth.ConcordServerAuth auth;

        @Inject
        public ConcordServerTokenProvider(ApiClientFactory apiClientFactory, Config config) {
            this.auth = initAuth(config);

            try {
                this.systemApi = new SystemApi(apiClientFactory.create(null));
            } catch (IOException e) {
                throw new RuntimeException("Error initializing System API client", e);
            }
        }

        private static ExternalTokenAuth.ConcordServerAuth initAuth(Config config) {
            if (!config.hasPath(CFG_ENABLED) || !config.getBoolean(CFG_ENABLED)) {
                return null;
            }

            return config.hasPath(CFG_URL_PATTERN)
                    ? ExternalTokenAuth.ConcordServerAuth.builder()
                            .urlPattern(Pattern.compile(config.getString(CFG_URL_PATTERN)))
                            .build()
                    : null;
        }

        @Override
        public boolean supports(URI repo, @Nullable Secret secret) {
            if (auth == null) {
                return false;
            }

            // Maybe support secret input? This requires elevated api access permission

            return auth.canHandle(repo);
        }

        @Override
        public Optional<ExpiringToken> getToken(URI repo, @Nullable Secret secret) {
            try {
                var resp = systemApi.getExternalToken(repo);

                return Optional.of(ExpiringToken.SimpleToken.builder()
                        .token(resp.getToken())
                        .username(resp.getUsername())
                        .expiresAt(resp.getExpiresAt())
                        .build());
            } catch (ApiException e) {
                if (e.getCode() == 403) {
                    // User needs externalTokenLookup permission
                    throw new RuntimeException("No permission to get auth token from concord server.");
                }

                throw new RuntimeException("Error retrieving concord-provided auth token", e);
            }
        }
    }

}
