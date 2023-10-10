package com.walmartlabs.concord.client2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Secret;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SecretClient {

    private static final int DEFAULT_RETRY_COUNT = 3;
    private static final long DEFAULT_RETRY_INTERVAL = 5000;

    private final ApiClient apiClient;
    private final int retryCount;
    private final long retryInterval;

    public SecretClient(ApiClient apiClient) {
        this(apiClient, DEFAULT_RETRY_COUNT, DEFAULT_RETRY_INTERVAL);
    }

    public SecretClient(ApiClient apiClient, int retryCount, long retryInterval) {
        this.apiClient = apiClient;
        this.retryCount = retryCount;
        this.retryInterval = retryInterval;
    }

    /**
     * Fetches a decrypted Concord secret from the server.
     */
    public <T extends Secret> T getData(String orgName, String secretName, String password, SecretEntryV2.TypeEnum expectedType) throws Exception {
        SecretsApi.GetSecretDataRequest req = new SecretsApi.GetSecretDataRequest()
                .storePassword(password);

        SecretsApi api = new SecretsApi(apiClient);

        ApiResponse<InputStream> r = null;
        try {
            r = ClientUtils.withRetry(retryCount, retryInterval,
                    () -> api.getSecretDataWithHttpInfo(orgName, secretName, req.asMap()));

            if (r.getData() == null) {
                throw new SecretNotFoundException(orgName, secretName);
            }

            String secretType = ClientUtils.getHeader(Constants.Headers.SECRET_TYPE, r);
            if (secretType == null) {
                throw new IllegalStateException("Can't determine the secret's expectedType. Server response: code=" + r.getStatusCode());
            }

            SecretEntryV2.TypeEnum actualSecretType = SecretEntryV2.TypeEnum.valueOf(secretType);

            if (expectedType != null && expectedType != actualSecretType) {
                String msg = "Unexpected type of %s/%s. Expected %s, got %s. " +
                        "Check the secret's expectedType and its usage - some secrets can only be used for specific purposes " +
                        "(e.g. %s is typically used for key-based authentication).";
                throw new IllegalArgumentException(String.format(msg, orgName, secretName, expectedType, actualSecretType, SecretEntryV2.TypeEnum.KEY_PAIR));
            }

            try (InputStream is = r.getData()) {
                return readSecret(actualSecretType, is.readAllBytes());
            }
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                throw new SecretNotFoundException(orgName, secretName);
            }
            throw e;
        } finally {
            if (r != null && r.getData() != null) {
                r.getData().close();
            }
        }
    }

    /**
     * Decrypt the provided string using the project's key.
     */
    public byte[] decryptString(UUID instanceId, byte[] input) throws Exception {
        ProcessApi api = new ProcessApi(apiClient);
        return ClientUtils.withRetry(retryCount, retryInterval, () -> api.decryptString(instanceId, input));
    }

    /**
     * Encrypts the provided string using the project's key.
     */
    public String encryptString(String orgName, String projectName, String input) throws Exception {
        ProjectsApi api = new ProjectsApi(apiClient);
        EncryptValueResponse r = ClientUtils.withRetry(retryCount, retryInterval,
                () -> api.encrypt(orgName, projectName, input));

        return r.getData();
    }

    /**
     * Creates a new Concord secret.
     */
    public SecretOperationResponse createSecret(CreateSecretRequest secretRequest) throws ApiException {
        String path = "/api/v1/org/" + secretRequest.org() + "/secret";

        Map<String, Object> params = new HashMap<>();
        params.put(Constants.Multipart.NAME, secretRequest.name());
        params.put(Constants.Multipart.GENERATE_PASSWORD, secretRequest.generatePassword());
        if (secretRequest.storePassword() != null) {
            params.put(Constants.Multipart.STORE_PASSWORD, secretRequest.storePassword());
        }

        SecretEntryV2.VisibilityEnum visibility = secretRequest.visibility();
        if (visibility != null) {
            params.put(Constants.Multipart.VISIBILITY, visibility.getValue());
        }

        if (secretRequest.projectIds() != null) {
            params.put(Constants.Multipart.PROJECT_IDS, secretRequest.projectIds().stream().map(UUID::toString).collect(Collectors.joining(",")));
        } else if (secretRequest.projectNames() != null) {
            params.put(Constants.Multipart.PROJECT_NAMES, String.join(",", secretRequest.projectNames()));
        }

        byte[] data = secretRequest.data();
        CreateSecretRequest.KeyPair keyPair = secretRequest.keyPair();
        CreateSecretRequest.UsernamePassword usernamePassword = secretRequest.usernamePassword();

        if (data != null) {
            params.put(Constants.Multipart.TYPE, SecretEntryV2.TypeEnum.DATA.getValue());
            params.put(Constants.Multipart.DATA, data);
        } else if (keyPair != null) {
            params.put(Constants.Multipart.TYPE, SecretEntryV2.TypeEnum.KEY_PAIR.getValue());
            params.put(Constants.Multipart.PUBLIC, readFile(keyPair.publicKey()));
            params.put(Constants.Multipart.PRIVATE, readFile(keyPair.privateKey()));
        } else if (usernamePassword != null) {
            params.put(Constants.Multipart.TYPE, SecretEntryV2.TypeEnum.USERNAME_PASSWORD.getValue());
            params.put(Constants.Multipart.USERNAME, usernamePassword.username());
            params.put(Constants.Multipart.PASSWORD, usernamePassword.password());
        } else {
            throw new IllegalArgumentException("Secret data, a key pair or username/password must be specified.");
        }

        SecretsApi api = new SecretsApi(apiClient);

        SecretOperationResponse response = ClientUtils.withRetry(retryCount, retryInterval,
                () -> api.createSecret(secretRequest.org(), params));
        return response;
    }

    public void updateSecret(String orgName, String secretName, UpdateSecretRequest request) throws ApiException {
        String path = "/api/v2/org/" + orgName + "/secret/" + secretName;

        Map<String, Object> params = new HashMap<>();
        params.put(Constants.Multipart.ORG_ID, request.newOrgId());
        params.put(Constants.Multipart.ORG_NAME, request.newOrgName());
        params.put("removeProjectLink", request.removeProjectLink());
        params.put("ownerId", request.newOwnerId());
        params.put(Constants.Multipart.STORE_PASSWORD, request.currentPassword());
        params.put("newStorePassword", request.newPassword());
        params.put(Constants.Multipart.NAME, request.newName());
        params.put(Constants.Multipart.VISIBILITY, request.newVisibility());
        if (request.newProjectIds() != null) {
            params.put(Constants.Multipart.PROJECT_IDS, request.newProjectIds().stream().map(UUID::toString).collect(Collectors.joining(",")));
        } else if (request.newProjectNames() != null) {
            params.put(Constants.Multipart.PROJECT_NAMES, String.join(",", request.newProjectNames()));
        }

        byte[] data = request.data();
        CreateSecretRequest.KeyPair keyPair = request.keyPair();
        CreateSecretRequest.UsernamePassword usernamePassword = request.usernamePassword();

        if (data != null) {
            params.put(Constants.Multipart.TYPE, SecretEntryV2.TypeEnum.DATA.getValue());
            params.put(Constants.Multipart.DATA, data);
        } else if (keyPair != null) {
            params.put(Constants.Multipart.TYPE, SecretEntryV2.TypeEnum.KEY_PAIR.getValue());
            params.put(Constants.Multipart.PUBLIC, readFile(keyPair.publicKey()));
            params.put(Constants.Multipart.PRIVATE, readFile(keyPair.privateKey()));
        } else if (usernamePassword != null) {
            params.put(Constants.Multipart.TYPE, SecretEntryV2.TypeEnum.USERNAME_PASSWORD.getValue());
            params.put(Constants.Multipart.USERNAME, usernamePassword.username());
            params.put(Constants.Multipart.PASSWORD, usernamePassword.password());
        }

        params.values().removeIf(Objects::isNull);

        SecretsV2Api api = new SecretsV2Api(apiClient);

        ClientUtils.withRetry(retryCount, retryInterval,
                () -> api.updateSecret(orgName, secretName, params));
    }

    private static byte[] readFile(Path file) {
        if (file == null) {
            return null;
        }

        if (Files.notExists(file)) {
            throw new IllegalArgumentException("File '" + file + "' not found");
        }

        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new RuntimeException("Error while reading " + file + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T readSecret(SecretEntryV2.TypeEnum type, byte[] bytes) {
        switch (type) {
            case DATA:
                return (T) new BinaryDataSecret(bytes);
            case KEY_PAIR:
                return (T) KeyPair.deserialize(bytes);
            case USERNAME_PASSWORD:
                return (T) UsernamePassword.deserialize(bytes);
            default:
                throw new IllegalArgumentException("unknown secret type: " + type);
        }
    }
}
