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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import com.walmartlabs.concord.repository.auth.AccessToken;
import com.walmartlabs.concord.repository.auth.AppInstallation;
import com.walmartlabs.concord.repository.auth.GitAuth;
import com.walmartlabs.concord.repository.auth.HttpAuthProvider;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.cfg.GitConfiguration;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class HttpAuthProviderImpl implements HttpAuthProvider {

    private final List<GitAuth> authConfigs;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, AppInstallationAccessToken> tokenCache = new ConcurrentHashMap<>();

    @Inject
    public HttpAuthProviderImpl(GitConfiguration gitCfg,
                                ObjectMapperProvider mapperProvider) {
        this.authConfigs = gitCfg.getAuthConfigs();
        this.objectMapper = mapperProvider.get();
    }

    @Override
    public boolean canHandle(URI repoUri) {
        return authConfigs.stream()
                .anyMatch(auth -> canHandle(auth, repoUri));
    }

    private static boolean canHandle(GitAuth auth, URI repoUri) {
        return auth.baseUrl().getHost().equals(repoUri.getHost());
    }

    @Override
    public String get(String gitHost, URI repo, @Nullable Secret secret) {
        return authConfigs.stream()
                .filter(auth -> canHandle(auth, repo))
                .findFirst()
                .map(auth -> {
                    if (auth instanceof AccessToken token) {
                         return token.token();
                    }

                    if (auth instanceof AppInstallation app) {
                        return getTokenFromAppInstall(app, repo);
                    }

                    throw new IllegalArgumentException("Unsupported GitAuth type for repo: " + repo);
                })
                .orElse(null); // TODO as long as we support git.oauth we wont throw an exception here
    }

    private String getTokenFromAppInstall(AppInstallation app, URI repo) {
        // Some folks give sloppy, but valid, urls like https://me123@github.com/my/repo.git/
        String repoUrl = repo.toString();
        String baseUrl = app.baseUrl().toString();
        String cleanedPath = repoUrl.replaceFirst(".*" + baseUrl, "")
                .replaceFirst("\\.git$", "");


        // parse out the owner/repo from the path
        var pathParts = Arrays.asList(cleanedPath.split("/")).stream()
                .filter(e -> e != null && !e.isBlank())
                .limit(2)
                .toList();


        if (pathParts.size() != 2) {
            throw new IllegalArgumentException("Failed to parse owner and repository from path: " + cleanedPath);
        }

        var ownerAndRepo = pathParts.get(0) + "/" + pathParts.get(1);

        return getToken(app, ownerAndRepo);
    }



    public String getToken(AppInstallation app, String orgRepo) {
        String cacheKey = app.clientId() + ":" + orgRepo;
        AppInstallationAccessToken cached = tokenCache.get(cacheKey);

        // Make sure we have at least 60 seconds before expiry
        if (cached != null && !isExpired(cached, 60)) {
            return cached.token();
        }

        try {
            var jwt = generateJWT(app);
            var accessTokenUrl = accessTokenUrl(app.apiUrl(), orgRepo, jwt);
            var newToken = createAccessToken(accessTokenUrl, jwt);
            tokenCache.put(cacheKey, newToken);
            return newToken.token();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    static boolean isExpired(AppInstallationAccessToken token, long buffer) {
        if (token == null) {
            return true;
        }

        return OffsetDateTime.now()
                .isAfter(token.expiresAt().minusSeconds(buffer));
    }

    String accessTokenUrl(String apiBaseUrl, String installationRepo, String jwt) {
        var req = HttpRequest.newBuilder().GET()
                .uri(URI.create(apiBaseUrl + "/repos/" + installationRepo + "/installation"))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build();

        try {
            var appInstallation = sendRequest(req, 200, GitHubAppInstallation.class);
            return appInstallation.accessTokensUrl();
        } catch (Exception e) {
            throw new RuntimeException("Error retrieving app installation", e);
        }
    }

    AppInstallationAccessToken createAccessToken(String accessTokenUrl, String jwt) {
        var req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(accessTokenUrl))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build();

        try {
            return sendRequest(req, 201, AppInstallationAccessToken.class);
        } catch (IOException e) {
            throw new RuntimeException("Error generating app access token", e);
        }
    }

    static String generateJWT(AppInstallation auth) throws Exception {


        var pk = Files.readString(auth.privateKey());

        var rsaJWK = JWK.parseFromPEMEncodedObjects(pk).toRSAKey();

        // Create RSA-signer with the private key
        var signer = new RSASSASigner(rsaJWK);

        // Prepare JWT with claims set
        var claimsSet = new JWTClaimsSet.Builder()
                .issueTime(new Date())
                .issuer(auth.clientId())
                .expirationTime(new Date(new Date().getTime() + 60 * 10 * 1000))
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
    @JsonDeserialize(as = ImmutableAppInstallationAccessToken.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public interface AppInstallationAccessToken {

        String token();

        @JsonProperty("expires_at")
        OffsetDateTime expiresAt();

    }


    @Value.Immutable
    @Value.Style(jdkOnly = true)
    @JsonDeserialize(as = ImmutableGitHubAppInstallation.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public interface GitHubAppInstallation {

        /*
        This is all we **need**, even though there's other attributes. Some may differ
        between GitHub "cloud" and GitHub Enterprise. So, be care if/when adding more.
         */
        @JsonProperty("access_tokens_url")
        String accessTokensUrl();

    }
}
