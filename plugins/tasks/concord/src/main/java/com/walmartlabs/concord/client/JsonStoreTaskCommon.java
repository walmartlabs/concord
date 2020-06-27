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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class JsonStoreTaskCommon {

    private static final Logger log = LoggerFactory.getLogger(JsonStoreTaskCommon.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    private final ApiClient apiClient;

    public JsonStoreTaskCommon(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void put(String orgName, String storeName, String itemPath, Object data) throws ApiException {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null.");
        }

        if (!(data instanceof Map)) {
            throw new IllegalArgumentException("Data must be a valid JSON object, represented by a Java Map instance. Got: " + data.getClass());
        }

        assertNotEmpty("Organization name", orgName);
        assertNotEmpty("Store name", storeName);
        assertNotEmpty("Item path", itemPath);

        log.info("Updating item '{}' (org={}, store={})", itemPath, orgName, storeName);

        ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            JsonStoreDataApi api = new JsonStoreDataApi(apiClient);
            return api.data(orgName, storeName, itemPath, data);
        });
    }

    public Object get(String orgName, String storeName, String itemPath) throws ApiException {
        assertNotEmpty("Organization name", orgName);
        assertNotEmpty("Store name", storeName);
        assertNotEmpty("Item path", itemPath);

        log.info("Getting item '{}' (org='{}', store='{}')", itemPath, orgName, storeName);

        // we need to deserialize the response using Jackson instead of GSON to avoid
        // differences between two libraries (e.g. deserialization of integers/decimals)
        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () ->
                RequestUtils.request(apiClient, "/api/v1/org/" + orgName + "/jsonstore/" + storeName + "/item/" + itemPath, "GET", null, Map.class));
    }

    public boolean delete(String orgName, String storeName, String itemPath) throws ApiException {
        assertNotEmpty("Organization name", orgName);
        assertNotEmpty("Store name", storeName);
        assertNotEmpty("Item path", itemPath);

        log.info("Removing item '{}' (org='{}', store='{}')", itemPath, orgName, storeName);

        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            JsonStoreDataApi api = new JsonStoreDataApi(apiClient);
            GenericOperationResult result = api.delete(orgName, storeName, itemPath);
            return result != null && result.getResult() == GenericOperationResult.ResultEnum.DELETED;
        });
    }

    public List<Object> executeQuery(String orgName, String storeName, String queryName, Map<String, Object> params) throws ApiException {
        assertNotEmpty("Organization name", orgName);
        assertNotEmpty("Store name", storeName);
        assertNotEmpty("Query name", queryName);

        log.info("Executing query '{}' (org='{}', store='{}') with parameters '{}'", queryName, orgName, storeName, params);

        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            JsonStoreQueryApi api = new JsonStoreQueryApi(apiClient);
            return api.exec(orgName, storeName, queryName, params);
        });
    }

    private static void assertNotEmpty(String what, String s) {
        if (s == null || s.isEmpty()) {
            throw new IllegalArgumentException(what + " cannot be empty or null");
        }
    }
}
