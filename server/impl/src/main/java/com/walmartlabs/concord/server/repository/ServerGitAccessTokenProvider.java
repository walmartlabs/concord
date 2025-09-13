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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.repository.RepositoryException;
import com.walmartlabs.concord.repository.auth.*;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.cfg.GitConfiguration;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

@Named
@Singleton
public class ServerGitAccessTokenProvider implements GitAccessTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ServerGitAccessTokenProvider.class);

    private final List<GitAuth> authConfigs;
    private final ObjectMapper objectMapper;

    private final LoadingCache<CacheKey, Optional<ActiveAccessToken>> cache;

    private record CacheKey(URI repoUri, Secret secret) {}

    @Inject
    public ServerGitAccessTokenProvider(GitConfiguration gitCfg,
                                        ObjectMapper objectMapper) {
        this.authConfigs = gitCfg.getAuthConfigs();
        this.objectMapper = objectMapper;
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(gitCfg.getSystemAuthCacheDuration())
                .recordStats()
                .maximumWeight(1024 * 10L) // ~ 10MB
                .weigher((Weigher<CacheKey, Optional<ActiveAccessToken>>) (key, value) -> {
                        int weight = 1;

                        if (key.secret() != null) {
                            weight += 1;

                            if (key.secret() instanceof BinaryDataSecret bds) {
                                weight += bds.getData().length / 1024;
                            } else if (key.secret() instanceof UsernamePassword up) {
                                weight += (up.getUsername().length() + up.getPassword().length) / 1024;
                            }
                        }

                        return weight;
                })
                .removalListener(k -> log.info("Removing token from cache: {}", k.getKey()))
                .build(new CacheLoader<>() {
                    @Override
                    public @Nonnull Optional<ActiveAccessToken> load(@Nonnull CacheKey key) {
                        log.info("Loading token to cache: {}", key);
                        return fetchToken(key.repoUri(), key.secret());
                    }
                });
    }

    @Override
    public boolean canHandle(URI repoUri) {
        return authConfigs.stream()
                .anyMatch(auth -> canHandle(auth, repoUri));
    }

    private static boolean canHandle(GitAuth auth, URI repoUri) {
        return auth.baseUrl().matches(repoUri.getHost());
    }

    @Override
    public Optional<ActiveAccessToken> getAccessToken(String gitHost, URI repo, @Nullable Secret secret) throws RepositoryException {
        try {
            var cacheKey = new CacheKey(repo, secret);
            var activeToken = cache.get(cacheKey);

            if (activeToken.isPresent() && activeToken.get().secondsUntilExpiration() < 10) {
                // not enough time to be useful
                cache.invalidate(cacheKey);
                return cache.get(cacheKey);
            }

            // refresh cache if the token is expiring soon, doesn't affect current token
            if (activeToken.isPresent() && activeToken.get().secondsUntilExpiration() < 300) {
                cache.refresh(cacheKey);
            }

            return activeToken;
        } catch (ExecutionException e) {
            throw new RepositoryException("Error retrieving access token for repo: " + repo, e);
        } catch (UncheckedExecutionException e) {
            // unwrap from guava
            if (e.getCause() instanceof RepositoryException repoEx) {
                throw repoEx;
            }

            log.warn("getAccessToken ['{}'] -> error: {}", repo,  e.getMessage());

            throw new RepositoryException("Unexpected error retrieving access token for repo: " + repo);
        }
    }

    private Optional<ActiveAccessToken> fetchToken(URI repo, @Nullable Secret secret) {
        if (secret != null) {
             return fromSecret(repo, secret);
        }

        return authConfigs.stream()
                .filter(auth -> canHandle(auth, repo))
                .findFirst()
                .map(auth -> {
                    if (auth instanceof AccessToken token) {
                        return ActiveAccessToken.builder()
                                .token(token.token())
                                .build();
                    }

                    if (auth instanceof AppInstallation app) {
                        return getTokenFromAppInstall(app, repo);
                    }

                    throw new IllegalArgumentException("Unsupported GitAuth type for repo: " + repo);
                });
    }

    private Optional<ActiveAccessToken> fromSecret(URI repo, @Nonnull Secret secret) {
        if (secret instanceof KeyPair) {
            return Optional.empty(); // we don't handle ssh keypairs here
        } else if (secret instanceof UsernamePassword) {
            return Optional.empty(); // no need to cache
        } else if (secret instanceof BinaryDataSecret bds) {
            return Optional.ofNullable(fromBinaryData(repo, bds));
        }

        return Optional.empty();
    }

    private ActiveAccessToken fromBinaryData(URI repo, BinaryDataSecret bds) {
        // TODO implement
        return null;
    }

    private ActiveAccessToken getTokenFromAppInstall(AppInstallation app, URI repo) {
        // Some folks give sloppy, but valid, urls like https://me123@github.com/my/repo.git/
        var repoUrl = repo.toString();
        var baseUrl = app.baseUrl();
        var cleanedPath = repoUrl.replaceFirst(".*" + baseUrl, "")
                .replaceFirst("\\.git$", "");


        // parse out the owner/repo from the path
        var pathParts = Arrays.stream(cleanedPath.split("/"))
                .filter(e -> !e.isBlank())
                .limit(2)
                .toList();

        if (pathParts.size() != 2) {
            throw new IllegalArgumentException("Failed to parse owner and repository from path: " + cleanedPath);
        }

        var ownerAndRepo = pathParts.get(0) + "/" + pathParts.get(1);

        return getToken(app, ownerAndRepo);
    }

    public ActiveAccessToken getToken(AppInstallation app, String orgRepo) throws RepositoryException {
        try {
            var jwt = generateJWT(app);
            var accessTokenUrl = getAccessTokenUrl(app.apiUrl(), orgRepo, jwt);
            return createAccessToken(accessTokenUrl, jwt);
        } catch (JOSEException e) {
            throw new RepositoryException("Error generating JWT for app: " + app, e);
        } catch (IOException e) {
            throw new RepositoryException("Error reading app private key: " + app, e);
        }
    }

    String getAccessTokenUrl(String apiBaseUrl, String installationRepo, String jwt) throws RepositoryException {
        var req = HttpRequest.newBuilder().GET()
                .uri(URI.create(apiBaseUrl + "/repos/" + installationRepo + "/installation"))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build();

            var appInstallation = sendRequest(req, 200, GitHubAppInstallation.class, (code, body) -> {
                log.warn("getAccessTokenUrl ['{}'] -> error: {} : {}", installationRepo, code, body);

                if (code == 404) {
                    // not possible to discern between repo not found and app not installed for existing (private) repo
                    return new RepositoryException.NotFoundException("Repo not found or App installation not found for repo");
                }

                return new RepositoryException("Unexpected error locating repo installation: " + code);
            });

            return appInstallation.accessTokensUrl();
    }

    ActiveAccessToken createAccessToken(String accessTokenUrl, String jwt) {
        var req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(accessTokenUrl))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build();

        return sendRequest(req, 201, ActiveAccessToken.class, (code, body) -> {
            log.warn("createAccessToken ['{}'] -> error: {} : {}", accessTokenUrl, code, body);

            if (code == 404) {
                // this would be pretty odd to hit, this means the url returned from the installation lookup is invalid
                return new RepositoryException.NotFoundException("App access token url not found");
            }

            return new RepositoryException("Unexpected error creating app access token: " + code);
        });
    }

    static String generateJWT(AppInstallation auth) throws IOException, JOSEException {
        var pk = Files.readString(auth.privateKey());
        var rsaJWK = JWK.parseFromPEMEncodedObjects(pk).toRSAKey();

        // Create RSA-signer with the private key
        var signer = new RSASSASigner(rsaJWK);

        // Prepare JWT with claims set
        var claimsSet = new JWTClaimsSet.Builder()
                .issueTime(new Date())
                .issuer(auth.clientId())
                .expirationTime(new Date(new Date().getTime() + 60 * 10 * 1000)) // TODO parameterize in config(s)
                .build();

        var signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(rsaJWK.getKeyID())
                        .build(),
                claimsSet);

        // Compute the RSA signature
        signedJWT.sign(signer);

        // To serialize to compact form, produces something like
        return signedJWT.serialize();
    }

    private <T> T sendRequest(HttpRequest httpRequest, int expectedCode, Class<T> clazz, BiFunction<Integer, String, RepositoryException> exFun) throws RepositoryException {
        try {
            var resp = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != expectedCode) {
                throw exFun.apply(resp.statusCode(), readBody(resp));
            }
            return objectMapper.readValue(resp.body(), clazz);
        } catch (IOException e) {
            throw new RepositoryException("Error sending request", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        throw new IllegalStateException("Unexpected error sending HTTP request");
    }

    String readBody(HttpResponse<InputStream> resp) throws IOException {
        try (var is = resp.body()) {
            return new String(is.readAllBytes());
        }
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    @JsonDeserialize(as = ImmutableGitHubAppInstallation.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public interface GitHubAppInstallation {

        /*
        This is all we **need**, even though there's other attributes. Some may differ
        between GitHub "cloud" and GitHub Enterprise/private. So, be care if/when adding more.
         */
        @JsonProperty("access_tokens_url")
        String accessTokensUrl();

    }
}
