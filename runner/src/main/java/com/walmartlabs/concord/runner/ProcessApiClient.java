package com.walmartlabs.concord.runner;

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
import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProcessApiClient {

    private static final String RETRY_COUNT_KEY = "api.retry.count";
    private static final String RETRY_INTERVAL_KEY = "api.retry.interval";

    private final ApiClient apiClient;

    private final int retryCount;
    private final long retryInterval;

    public ProcessApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;

        this.retryCount = Integer.parseInt(getEnv(RETRY_COUNT_KEY, "3"));
        this.retryInterval = Integer.parseInt(getEnv(RETRY_INTERVAL_KEY, "5000"));
    }

    public void uploadCheckpoints(UUID instanceId, UUID checkpointId, String checkpointName, Path data) throws ApiException {
        String path = "/api/v1/process/" + instanceId + "/checkpoint/" + checkpointId;

        ClientUtils.withRetry(retryCount, retryInterval, () -> {
            ClientUtils.postData(apiClient, path, data.toFile(), null);
            return null;
        });
    }

    private static String getEnv(String key, String def) {
        String value = System.getProperty(key);
        if (value != null) {
            return value;
        }
        if (def != null) {
            return def;
        }
        throw new IllegalArgumentException(key + " must be specified");
    }
}
