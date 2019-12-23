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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.ApiResponse;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Secret;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class SecretClient {

    private static final int RETRY_COUNT = 3;

    private static final long RETRY_INTERVAL = 5000;

    private final ApiClient apiClient;

    public SecretClient(ApiClient apiClient) {
        this.apiClient = apiClient;
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
            r = ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL,
                    () -> ClientUtils.postData(apiClient, path, params, File.class));

            if (r.getData() == null) {
                throw new IllegalArgumentException("Secret not found");
            }

            SecretEntry.TypeEnum actualSecretType = SecretEntry.TypeEnum.valueOf(ClientUtils.getHeader(Constants.Headers.SECRET_TYPE, r));

            if (type != null && type != actualSecretType) {
                throw new IllegalArgumentException("Expected " + type + " got " + actualSecretType);
            }

            return readSecret(actualSecretType, Files.readAllBytes(r.getData().toPath()));
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                throw new IllegalArgumentException("Secret not found");
            }
            throw e;
        } finally {
            if (r != null && r.getData() != null) {
                Files.delete(r.getData().toPath());
            }
        }
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
