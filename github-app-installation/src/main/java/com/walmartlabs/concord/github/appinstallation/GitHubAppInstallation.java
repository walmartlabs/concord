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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.walmartlabs.concord.common.AuthTokenProvider;
import com.walmartlabs.concord.common.ExternalAuthToken;
import com.walmartlabs.concord.common.cfg.ExternalTokenAuth;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.github.appinstallation.cfg.GitHubAppInstallationConfig;
import com.walmartlabs.concord.github.appinstallation.exception.GitHubAppException;
import com.walmartlabs.concord.github.appinstallation.exception.RepoExtractionException;
import com.walmartlabs.concord.sdk.Secret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class GitHubAppInstallation implements AuthTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(com.walmartlabs.concord.github.appinstallation.GitHubAppInstallation.class);

    private final GitHubAppInstallationConfig cfg;
    private final AccessTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    private final LoadingCache<CacheKey, Optional<ExternalAuthToken>> cache;

    private record CacheKey(URI repoUri, Secret secret) {}

    @Inject
    public GitHubAppInstallation(GitHubAppInstallationConfig cfg, ObjectMapper objectMapper) {
        this.cfg = cfg;
        this.objectMapper = objectMapper;
        this.tokenProvider = new AccessTokenProvider(cfg, objectMapper);

        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(cfg.getSystemAuthCacheDuration())
                .maximumWeight(cfg.getSystemAuthCacheMaxWeight())
                .weigher((Weigher<CacheKey, Optional<ExternalAuthToken>>) (key, value) -> {
                    int weight = 1;

                    if (key.secret() != null) {
                        weight += 1;

                        if (key.secret() instanceof BinaryDataSecret bds) {
                            weight += bds.getData().length / 1024;
                        }
                    }

                    return weight;
                })
                .build(new CacheLoader<>() {
                    @Override
                    public @Nonnull Optional<ExternalAuthToken> load(@Nonnull CacheKey key) {
                        return fetchToken(key.repoUri(), key.secret());
                    }
                });
    }

    @Override
    public boolean supports(URI repo, @Nullable Secret secret) {
        return Utils.validateSecret(secret, objectMapper) || systemSupports(repo);
    }

    @Override
    public Optional<ExternalAuthToken> getToken(URI repo, @Nullable Secret secret) {
        try {
            var cacheKey = new CacheKey(repo, secret);
            var activeToken = cache.get(cacheKey);

            return activeToken.map(t -> refreshBeforeExpire(t, cacheKey));
        } catch (ExecutionException e) {
            throw new GitHubAppException("Error retrieving access token for repo: " + repo, e);
        } catch (UncheckedExecutionException e) {
            // unwrap from guava
            if (e.getCause() instanceof GitHubAppException repoEx) {
                throw repoEx;
            }

            log.warn("getAccessToken ['{}'] -> error: {}", repo,  e.getMessage());

            throw new GitHubAppException("Unexpected error retrieving access token for repo: " + repo);
        }
    }

    public long cacheSize() {
        return cache.size();
    }

    private boolean systemSupports(URI repoUri) {
        return cfg.getAuthConfigs().stream().anyMatch(auth -> auth.canHandle(repoUri));
    }

    private Optional<ExternalAuthToken> fetchToken(URI repo, @Nullable Secret secret) {
        if (secret != null) {
            return fromSecret(repo, secret);
        }

        // no secret, see if system config has something for this repo
        return cfg.getAuthConfigs().stream()
                .filter(auth -> auth.canHandle(repo))
                .findFirst()
                .map(auth -> {
                    if (auth instanceof ExternalTokenAuth.Oauth tokenAuth) {
                        return GitHubInstallationToken.builder()
                                .token(tokenAuth.token())
                                .username(tokenAuth.username().orElse(null))
                                .build();
                    }

                    if (auth instanceof AppInstallationAuth app) {
                        return getTokenFromAppInstall(app, repo);
                    }

                    throw new IllegalArgumentException("Unsupported GitAuth type for repo: " + repo);
                });
    }

    /**
     * Cache may return a token that's close to expiring. If it's too close,
     * invalidate and get a new one. If it's just a little close, refresh the
     * cache in the background and return the still-active token.
     */
    private ExternalAuthToken refreshBeforeExpire(@Nonnull ExternalAuthToken token, CacheKey cacheKey) {
        if (token.secondsUntilExpiration() < 10) {
            // not enough time to be useful. get a new token right now
            cache.invalidate(cacheKey);
            try {
                return cache.get(cacheKey).orElse(null);
            } catch (ExecutionException e) {
                throw new GitHubAppException("Error retrieving access token for repo: " + cacheKey.repoUri(), e);
            }
        }

        // refresh cache if the token is expiring soon, doesn't affect current token
        if (token.secondsUntilExpiration() < 300) {
            cache.refresh(cacheKey);
        }

        return token;
    }

    private Optional<ExternalAuthToken> fromSecret(URI repo, @Nonnull Secret secret) {
        if (secret instanceof BinaryDataSecret bds) {
            return Optional.ofNullable(fromBinaryData(repo, bds));
        }

        return Optional.empty();
    }

    private ExternalAuthToken fromBinaryData(URI repo, BinaryDataSecret bds) {
        var appInfo = Utils.parseAppInstallation(bds, objectMapper);
        if (appInfo.isPresent()) {
            // great, it's apparently a valid app installation config
            return getTokenFromAppInstall(appInfo.get(), repo);
        }

        // hopefully it's just a token a plaintext token
        return GitHubInstallationToken.builder()
                .token(new String(bds.getData()).trim())
                .build();
    }

    private ExternalAuthToken getTokenFromAppInstall(AppInstallationAuth app, URI repo) {
        log.info("getTokenFromAppInstall ['{}', '{}']", app.apiUrl(), repo);

        try {
            var ownerAndRepo = Utils.extractOwnerAndRepo(app, repo);
            return accessTokenProvider().getRepoInstallationToken(app, ownerAndRepo);
        } catch (RepoExtractionException e) {
            log.warn("Error retrieving GitHub access token", e);
        }

        return null;
    }

    AccessTokenProvider accessTokenProvider() {
        return tokenProvider;
    }

}
