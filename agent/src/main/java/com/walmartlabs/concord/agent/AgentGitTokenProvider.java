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
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.walmartlabs.concord.agent.cfg.GitConfiguration;
import com.walmartlabs.concord.agent.remote.ApiClientFactory;
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.SystemApi;
import com.walmartlabs.concord.common.ExpiringToken;
import com.walmartlabs.concord.common.GitAuth;
import com.walmartlabs.concord.common.GitTokenProvider;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.github.appinstallation.AppInstallation;
import com.walmartlabs.concord.repository.RepositoryException;
import com.walmartlabs.concord.sdk.Secret;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public class AgentGitTokenProvider implements GitTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(AgentGitTokenProvider.class);
    private static final Pattern START_JSON_OBJECT = Pattern.compile("^\\s*\\{.*", Pattern.DOTALL);

    private final List<GitAuth> authConfigs;
    private final ApiClient apiClient;
    private final ObjectMapper objectMapper;

    public AgentGitTokenProvider(GitConfiguration gitCfg,
                                 ApiClientFactory apiClientFactory,
                                 ObjectMapper objectMapper) throws IOException {
        this.authConfigs = gitCfg.getAuthConfigs();
        this.objectMapper = objectMapper;
        this.apiClient = apiClientFactory.create(null);
    }

    @Override
    public boolean supports(URI repo, Secret secret) {
        return validateSecret(secret) || systemSupports(repo);
    }
    /**
     * Validates given secret is usable enough to attempt a remote lookup. Not
     * guaranteed to actually work, just a sanity check.
     */
    private boolean validateSecret(Secret secret) {
        // secret must be one of:
        // * plaintext token
        // * JSON-formatted GitHub app installation details: clientId, privateKey, apiUrl

        if (secret == null) {
            return false;
        }

        if (!(secret instanceof BinaryDataSecret bds)) {
            // this class is not the place for handling key pairs or username/password
            return false;
        } else {
            String data = new String(bds.getData());
            if (START_JSON_OBJECT.matcher(data).matches()) { // looks like JSON, starts with '{'
                try {
                    objectMapper.readValue(data, AppInstallation.class);
                    return true;
                } catch (IOException e) {
                    return false;
                }
            } else {
                // >0 length, printable ascii (no newlines, etc)
                return data.matches("[ -~]+");
            }
        }
    }

    public boolean systemSupports(URI repoUri) {
        return authConfigs.stream().anyMatch(auth -> auth.canHandle(repoUri));
    }

    @Override
    public Optional<ExpiringToken> getAccessToken(String gitHost, URI repo, @Nullable Secret secret) {
        if (secret != null) {
            return fromSecret(repo, secret);
        }

        return authConfigs.stream()
                .filter(auth -> auth.canHandle(repo))
                .findFirst()
                .map(auth -> {
                    if (auth instanceof GitAuth.ConcordServer) { // TODO do this as default/fallback? actually, maybe we enrich it with a different api token from system?
                        return getTokenFromConcordServer(gitHost, repo);
                    }

                    if (auth instanceof GitAuth.Oauth token) {
                        return ExpiringToken.StaticToken.builder()
                                .token(token.token())
                                .build();
                    }

                    if (auth instanceof AppInstallation app) {
                        return getTokenFromAppInstall(app, repo);
                    }

                    throw new IllegalArgumentException("Unsupported GitAuth type for repo: " + repo);
                });
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
        return ExpiringToken.StaticToken.builder()
                .token(new String(bds.getData()).trim())
                .build();
    }

    private Optional<AppInstallation> parseAppInstallation(BinaryDataSecret bds) {
        Map<?, ?> base;

        try { // find out if it's at least valid JSON.
            base = objectMapper.readValue(bds.getData(), Map.class);
        } catch (Exception e) {
            // invalid JSON, may be a plaintext token
            return Optional.empty();
        }

        try { // great, now convert it to the expected structure
            return Optional.of(objectMapper.convertValue(base, AppInstallation.class));
        } catch (IllegalArgumentException e) {
            // doesn't match the expected structure
            throw new RepositoryException("Invalid app installation definition.", e);
        }
    }

    ExpiringToken getTokenFromConcordServer(String gitHost, URI repository) {
        var systemApi = new SystemApi(apiClient);

        try {
            var resp = systemApi.getSystemGitAuth(gitHost, repository);

            return ExpiringToken.Impl.builder()
                    .token(resp.getToken())
                    .expiresAt(resp.getExpiresAt())
                    .build();
        } catch (ApiException e) {
            throw new RuntimeException("Error retrieving system git auth", e);
        }
    }

    protected ExpiringToken getTokenFromAppInstall(AppInstallation app, URI repo) {
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

    public ExpiringToken getToken(AppInstallation app, String orgRepo) throws RepositoryException {
        try {
            var jwt = generateJWT(app);
            var accessTokenUrl = getAccessTokenUrl(app.apiUrl(), orgRepo, jwt);
            return createAccessToken(accessTokenUrl, jwt);
        } catch (JOSEException e) {
            throw new RepositoryException("Error generating JWT for app: " + app.clientId(), e);
        } catch (IOException e) {
            throw new RepositoryException("Error reading app private key: " + app.clientId(), e);
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

    ExpiringToken createAccessToken(String accessTokenUrl, String jwt) {
        var req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(accessTokenUrl))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build();

        return sendRequest(req, 201, ExpiringToken.class, (code, body) -> {
            log.warn("createAccessToken ['{}'] -> error: {} : {}", accessTokenUrl, code, body);

            if (code == 404) {
                // this would be pretty odd to hit, this means the url returned from the installation lookup is invalid
                return new RepositoryException.NotFoundException("App access token url not found");
            }

            return new RepositoryException("Unexpected error creating app access token: " + code);
        });
    }

    static String generateJWT(AppInstallation auth) throws IOException, JOSEException {
        var pk = auth.privateKey();
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
