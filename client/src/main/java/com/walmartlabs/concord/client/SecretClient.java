package com.walmartlabs.concord.client;

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

import com.google.gson.reflect.TypeToken;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.ApiResponse;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Secret;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    public <T extends Secret> T getData(String orgName, String secretName, String password, SecretEntry.TypeEnum type) throws Exception {
        String path = "/api/v1/org/" + orgName + "/secret/" + secretName + "/data";

        ApiResponse<File> r = null;

        Map<String, Object> params = new HashMap<>();
        String pwd = password;
        if (password == null) {
            pwd = ""; // NOSONAR
        }
        params.put("storePassword", pwd);

        try {
            r = ClientUtils.withRetry(retryCount, retryInterval,
                    () -> ClientUtils.postData(apiClient, path, params, File.class));

            if (r.getData() == null) {
                throw new IllegalArgumentException("Secret not found: " + orgName + "/" + secretName);
            }

            SecretEntry.TypeEnum actualSecretType = SecretEntry.TypeEnum.valueOf(ClientUtils.getHeader(Constants.Headers.SECRET_TYPE, r));

            if (type != null && type != actualSecretType) {
                String msg = "Unexpected type of %s/%s. Expected %s, got %s. " +
                        "Check the secret's type and its usage - some secrets can only be used for specific purposes " +
                        "(e.g. %s is typically used for key-based authentication).";
                throw new IllegalArgumentException(String.format(msg, orgName, secretName, type, actualSecretType, SecretEntry.TypeEnum.KEY_PAIR));
            }

            return readSecret(actualSecretType, Files.readAllBytes(r.getData().toPath()));
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                throw new IllegalArgumentException("Secret not found: " + orgName + "/" + secretName);
            }
            throw e;
        } finally {
            if (r != null && r.getData() != null) {
                Files.delete(r.getData().toPath());
            }
        }
    }

    public byte[] decryptString(UUID instanceId, byte[] input) throws Exception {
        String path = "/api/v1/process/" + instanceId + "/decrypt";

        try {
            ApiResponse<byte[]> r = ClientUtils.withRetry(retryCount, retryInterval, () -> {
                Type returnType = new TypeToken<byte[]>() {
                }.getType();
                return ClientUtils.postData(apiClient, path, input, returnType);
            });
            return r.getData();
        } catch (ApiException e) {
            if (e.getCode() == 400) {
                throw new IllegalArgumentException("Can't decrypt the string: " + Base64.getEncoder().encodeToString(input));
            }
            throw e;
        }
    }

    public String encryptString(UUID instanceId, String orgName, String projectName, String input) throws Exception {
        String path = "/api/v1/org/" + orgName + "/project/" + projectName + "/encrypt";
        Map<String, String> headerParams = new HashMap<>();
        headerParams.put("Content-Type", "text/plain;charset=UTF-8");
        ApiResponse<EncryptValueResponse> r = ClientUtils.withRetry(retryCount, retryInterval,
                () -> ClientUtils.postData(apiClient, path, input, headerParams, EncryptValueResponse.class));

        if (r.getStatusCode() == 200 && r.getData().isOk()) {
            return r.getData().getData();
        }

        throw new ApiException("Error encrypting string. Status code:" + r.getStatusCode() + " Data: " + r.getData());
    }

    @SuppressWarnings("unchecked")
    private static <T> T readSecret(SecretEntry.TypeEnum type, byte[] bytes) {
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
