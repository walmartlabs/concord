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
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.walmartlabs.concord.common.ExternalAuthToken;
import com.walmartlabs.concord.github.appinstallation.cfg.GitHubAppInstallationConfig;
import com.walmartlabs.concord.github.appinstallation.exception.GitHubAppException;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Date;
import java.util.function.BiFunction;

public class AccessTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenProvider.class);

    private final HttpClient httpClient;
    private final Duration httpTimeout;
    private final ObjectMapper objectMapper;

    public AccessTokenProvider(GitHubAppInstallationConfig cfg, ObjectMapper objectMapper) {
        this.httpTimeout = cfg.getHttpClientTimeout();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(httpTimeout)
                .build();
    }

    public AccessTokenProvider(GitHubAppInstallationConfig cfg,
                               ObjectMapper objectMapper,
                               HttpClient httpClient) {
        this.httpTimeout = cfg.getHttpClientTimeout();
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    ExternalAuthToken getRepoInstallationToken(GitHubAppAuthConfig app, String orgRepo) throws GitHubAppException {
        try {
            var jwt = generateJWT(app);
            var accessTokenUrl = getAccessTokenUrl(app.apiUrl(), orgRepo, jwt);
            return ExternalAuthToken.StaticToken.builder()
                    .from(createAccessToken(accessTokenUrl, jwt))
                    .authId(app.id())
                    .username(app.username())
                    .build();
        } catch (JOSEException e) {
            throw new GitHubAppException("Error generating JWT for app: " + app.clientId());
        }
    }

    private String getAccessTokenUrl(String apiBaseUrl, String installationRepo, String jwt) throws GitHubAppException {
        var req = HttpRequest.newBuilder().GET()
                .uri(URI.create(apiBaseUrl + "/repos/" + installationRepo + "/installation"))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(httpTimeout)
                .build();

        var appInstallation = sendRequest(req, 200, AccessTokenProvider.GitHubAppInstallationResp.class, (code, body) -> {
            if (code == 404) {
                // not possible to discern between repo not found and app not installed for existing (private) repo
                log.warn("getAccessTokenUrl ['{}'] -> not found", installationRepo);
                return new GitHubAppException.NotFoundException("Repo not found or App installation not found for repo");
            }

            log.warn("getAccessTokenUrl ['{}'] -> error: {} : {}", installationRepo, code, body);
            return new GitHubAppException("Unexpected error locating repo installation: " + code);
        });

        return appInstallation.accessTokensUrl();
    }

    private ExternalAuthToken createAccessToken(String accessTokenUrl, String jwt) {
        var req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(accessTokenUrl))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(httpTimeout)
                .build();

        return sendRequest(req, 201, ExternalAuthToken.class, (code, body) -> {
            log.warn("createAccessToken ['{}'] -> error: {} : {}", accessTokenUrl, code, body);

            if (code == 404) {
                // this would be pretty odd to hit, this means the url returned from the installation lookup is invalid
                return new GitHubAppException.NotFoundException("App access token url not found");
            }

            return new GitHubAppException("Unexpected error creating app access token: " + code);
        });
    }

    private static String generateJWT(GitHubAppAuthConfig auth) throws JOSEException {
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

        // Serialize to compact form
        return signedJWT.serialize();
    }

    private <T> T sendRequest(HttpRequest httpRequest, int expectedCode, Class<T> clazz, BiFunction<Integer, String, GitHubAppException> exFun) throws GitHubAppException {
        try {
            var resp = httpClient.send(httpRequest, BodyHandlers.ofInputStream());
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

    private static String readBody(HttpResponse<InputStream> resp) throws IOException {
        try (var is = resp.body()) {
            return new String(is.readAllBytes());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubAppInstallationResp(
            /*
                This is the only attribute we **need**, even though there's other
                attributes. Some may differ between GitHub "cloud" and GitHub
                Enterprise/private. Be care if/when adding more.
             */
            @JsonProperty("access_tokens_url")
            String accessTokensUrl
    ) {
    }
}
