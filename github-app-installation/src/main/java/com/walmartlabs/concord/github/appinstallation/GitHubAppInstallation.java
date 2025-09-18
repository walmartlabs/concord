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
import com.walmartlabs.concord.common.AuthTokenProvider;
import com.walmartlabs.concord.common.ExpiringToken;
import com.walmartlabs.concord.common.cfg.ExternalTokenAuth;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.github.appinstallation.cfg.GithubAppInstallationConfig;
import com.walmartlabs.concord.sdk.Secret;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public class GitHubAppInstallation implements AuthTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(com.walmartlabs.concord.github.appinstallation.GitHubAppInstallation.class);
    private static final Pattern START_JSON_OBJECT = Pattern.compile("^\\s*\\{.*", Pattern.DOTALL);

    private final List<ExternalTokenAuth> authConfigs;
    private final Duration httpTimeout;
    private final ObjectMapper objectMapper;

    private final LoadingCache<CacheKey, Optional<ExpiringToken>> cache;

    private record CacheKey(URI repoUri, Secret secret) {}

    @Inject
    public GitHubAppInstallation(GithubAppInstallationConfig cfg, ObjectMapper objectMapper) {
        this.authConfigs = cfg.getAuthConfigs();
        this.httpTimeout = cfg.getHttpClientTimeout();
        this.objectMapper = objectMapper;

        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(cfg.getSystemAuthCacheDuration())
                .maximumWeight(cfg.getSystemAuthCacheMaxWeight())
                .weigher((Weigher<CacheKey, Optional<ExpiringToken>>) (key, value) -> {
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
                .build(new CacheLoader<>() {
                    @Override
                    public @Nonnull Optional<ExpiringToken> load(@Nonnull CacheKey key) {
                        return fetchToken(key.repoUri(), key.secret());
                    }
                });
    }

    @Override
    public boolean supports(URI repo, @Nullable Secret secret) {
        return validateSecret(secret) || systemSupports(repo);
    }

    @Override
    public Optional<ExpiringToken> getToken(URI repo, @Nullable Secret secret) {
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

    /**
     * Validates given secret is usable enough to attempt a remote lookup. Not
     * guaranteed to actually work, just a sanity check.
     */
    private boolean validateSecret(Secret secret) {
        // secret must be JSON-formatted GitHub app installation details: clientId, privateKey, apiUrl

        if (secret == null) {
            return false;
        }

        if (secret instanceof BinaryDataSecret bds) {
            var data = new String(bds.getData());
            if (START_JSON_OBJECT.matcher(data).matches()) { // looks like JSON, starts with '{'
                try {
                    objectMapper.readValue(data, AppInstallationAuth.class);
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }
        }

        // this class is not the place for handling key pairs or username/password
        return false;
    }

    public boolean systemSupports(URI repoUri) {
        return authConfigs.stream().anyMatch(auth -> auth.canHandle(repoUri));
    }

    private Optional<ExpiringToken> fetchToken(URI repo, @Nullable Secret secret) {
        if (secret != null) {
            return fromSecret(repo, secret);
        }

        // no secret, see if system config has something for this repo
        return authConfigs.stream()
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
    private ExpiringToken refreshBeforeExpire(@Nonnull ExpiringToken token, CacheKey cacheKey) {
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

    private Optional<ExpiringToken> fromSecret(URI repo, @Nonnull Secret secret) {
        if (secret instanceof KeyPair) {
            return Optional.empty(); // we don't handle ssh keypairs here
        } else if (secret instanceof UsernamePassword) {
            return Optional.empty(); // no need to cache
        } else if (secret instanceof BinaryDataSecret bds) {
            return Optional.ofNullable(fromBinaryData(repo, bds));
        }

        return Optional.empty();
    }

    private ExpiringToken fromBinaryData(URI repo, BinaryDataSecret bds) {
        var appInfo = parseAppInstallation(bds);
        if (appInfo.isPresent()) {
            return getTokenFromAppInstall(appInfo.get(), repo);
        }

        // should be a token
        return GitHubInstallationToken.builder()
                .token(new String(bds.getData()).trim())
                .build();
    }

    private Optional<AppInstallationAuth> parseAppInstallation(BinaryDataSecret bds) {
        Map<?, ?> base;

        try { // find out if it's at least valid JSON.
            base = objectMapper.readValue(bds.getData(), Map.class);
        } catch (Exception e) {
            // invalid JSON, may be a plaintext token
            return Optional.empty();
        }

        try { // great, now convert it to the expected structure
            return Optional.of(objectMapper.convertValue(base, AppInstallationAuth.class));
        } catch (IllegalArgumentException e) {
            // doesn't match the expected structure
            throw new GitHubAppException("Invalid app installation definition.", e);
        }
    }

    public ExpiringToken getTokenFromAppInstall(AppInstallationAuth app, URI repo) {
        log.info("getTokenFromAppInstall ['{}', '{}']", app.apiUrl(), repo);

        // Some folks give sloppy, but valid, urls like https://me123@github.com/my/repo.git/
        var repoUrl = repo.toString();
        var urlPattern = app.urlPattern();
        var cleanedPath = repoUrl.replaceFirst(".*" + urlPattern, "")
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
    public ExpiringToken getToken(AppInstallationAuth app, String orgRepo) throws GitHubAppException {
        try {
            var jwt = generateJWT(app);
            var accessTokenUrl = getAccessTokenUrl(app.apiUrl(), orgRepo, jwt);
            return ExpiringToken.SimpleToken.builder()
                    .from(createAccessToken(accessTokenUrl, jwt))
                    .username(app.username().orElse(null))
                    .build();
        } catch (JOSEException e) {
            throw new GitHubAppException("Error generating JWT for app: " + app.clientId());
        }
    }

    String getAccessTokenUrl(String apiBaseUrl, String installationRepo, String jwt) throws GitHubAppException {
        var req = HttpRequest.newBuilder().GET()
                .uri(URI.create(apiBaseUrl + "/repos/" + installationRepo + "/installation"))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(httpTimeout)
                .build();

        var appInstallation = sendRequest(req, 200, GitHubAppInstallationResp.class, (code, body) -> {
            log.warn("getAccessTokenUrl ['{}'] -> error: {} : {}", installationRepo, code, body);

            if (code == 404) {
                // not possible to discern between repo not found and app not installed for existing (private) repo
                log.warn("getAccessTokenUrl ['{}'] -> not found", installationRepo);
                return new GitHubAppException.NotFoundException("Repo not found or App installation not found for repo");
            }

            return new GitHubAppException("Unexpected error locating repo installation: " + code);
        });

        return appInstallation.accessTokensUrl();
    }

    ExpiringToken createAccessToken(String accessTokenUrl, String jwt) {
        var req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(accessTokenUrl))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(httpTimeout)
                .build();

        return sendRequest(req, 201, ExpiringToken.class, (code, body) -> {
            log.warn("createAccessToken ['{}'] -> error: {} : {}", accessTokenUrl, code, body);

            if (code == 404) {
                // this would be pretty odd to hit, this means the url returned from the installation lookup is invalid
                return new GitHubAppException.NotFoundException("App access token url not found");
            }

            return new GitHubAppException("Unexpected error creating app access token: " + code);
        });
    }

    static String generateJWT(AppInstallationAuth auth) throws JOSEException {
        var pk = auth.privateKey();
        var rsaJWK = JWK.parseFromPEMEncodedObjects(pk).toRSAKey();

        // Create RSA-signer with the private key
        var signer = new RSASSASigner(rsaJWK);

        // Prepare JWT with claims set
        var claimsSet = new JWTClaimsSet.Builder()
                .issueTime(new Date())
                .issuer(auth.clientId())
                // JWT expiration. GH requires less than 10 minutes
                .expirationTime(new Date(new Date().getTime() + Duration.ofMinutes(10).toMillis()))
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

    private <T> T sendRequest(HttpRequest httpRequest, int expectedCode, Class<T> clazz, BiFunction<Integer, String, GitHubAppException> exFun) throws GitHubAppException {
        try {
            var resp = HttpClient.newBuilder() // would be nice to re-use client thread-safely
                    .connectTimeout(httpTimeout)
                    .build()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != expectedCode) {
                throw exFun.apply(resp.statusCode(), readBody(resp));
            }
            return objectMapper.readValue(resp.body(), clazz);
        } catch (IOException e) {
            throw new GitHubAppException("Error sending request", e);
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
    @JsonDeserialize(as = ImmutableGitHubAppInstallationResp.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public interface GitHubAppInstallationResp {

        /*
        This is all we **need**, even though there's other attributes. Some may differ
        between GitHub "cloud" and GitHub Enterprise/private. So, be care if/when adding more.
         */
        @JsonProperty("access_tokens_url")
        String accessTokensUrl();

    }
}
