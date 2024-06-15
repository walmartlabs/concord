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

import com.walmartlabs.concord.client2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Common code for both v1 and v2 versions of the JSON store task.
 */
public class JsonStoreTaskCommon {

    private static final Logger log = LoggerFactory.getLogger(JsonStoreTaskCommon.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    private final ApiClient apiClient;

    public JsonStoreTaskCommon(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Returns {@code true} if the specified JSON store exists.
     */
    public boolean isStoreExists(String orgName, String storeName) throws ApiException {
        assertNotEmpty("Organization name", orgName);
        assertNotEmpty("Store name", storeName);

        JsonStoreEntry entry = ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            try {
                JsonStoreApi api = new JsonStoreApi(apiClient);
                return api.getJsonStore(orgName, storeName);
            } catch (ApiException e) {
                if (e.getCode() == 404) {
                    return null;
                }

                throw e;
            }
        });

        return entry != null;
    }

    /**
     * Returns {@code true} if the specified item and JSON store exists.
     * <p/>
     * The difference between this method and {@code get(orgName, storeName, itemPath) != null}
     * is that this method doesn't throw exceptions if the specified organization or the store
     * don't exist.
     */
    public boolean isExists(String orgName, String storeName, String itemPath) throws ApiException {
        try {
            return get(orgName, storeName, itemPath) != null;
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                return false;
            }

            throw e;
        }
    }

    /**
     * Creates a new JSON store or updates an existing one.
     */
    public void createOrUpdateStore(String orgName, JsonStoreRequest request) throws ApiException {
        ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            JsonStoreApi api = new JsonStoreApi(apiClient);
            GenericOperationResult result = api.createOrUpdateJsonStore(orgName, request);
            log.info("The store '{}' has been successfully {}", request.getName(), result.getResult());
            return null;
        });
    }

    /**
     * Creates a new JSON store query or update an existing one.
     */
    public void createOrUpdateQuery(String orgName, String storeName, String queryName, String queryText) throws ApiException {
        ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            JsonStoreQueryApi api = new JsonStoreQueryApi(apiClient);
            JsonStoreQueryRequest request = new JsonStoreQueryRequest()
                    .name(queryName)
                    .text(queryText);
            GenericOperationResult result = api.createOrUpdateJsonStoreQuery(orgName, storeName, request);
            log.info("The query '{}' in store '{}' has been successfully {}", queryName, storeName, result.getResult());
            return null;
        });

    }

    /**
     * Inserts a new item or replaces an existing one.
     * <p/>
     * If {@code createStore} is {@code true} the store will be automatically created.
     */
    public void put(String orgName, String storeName, String itemPath, Object data, boolean createStore) throws ApiException {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null.");
        }

        if (!(data instanceof Map)) {
            throw new IllegalArgumentException("Data must be a valid JSON object, represented by a Java Map instance. Got: " + data.getClass());
        }

        assertNotEmpty("Organization name", orgName);
        assertNotEmpty("Store name", storeName);
        assertNotEmpty("Item path", itemPath);

        if (createStore && !isStoreExists(orgName, storeName)) {
            createOrUpdateStore(orgName, new JsonStoreRequest().name(storeName));
        }

        log.info("Updating item '{}' (org={}, store={})", itemPath, orgName, storeName);

        ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            JsonStoreDataApi api = new JsonStoreDataApi(apiClient);
            return api.updateJsonStoreData(orgName, storeName, itemPath, data);
        });
    }

    /**
     * Returns an existing item or {@code null} if the specified path doesn't exist.
     */
    public Object get(String orgName, String storeName, String itemPath) throws ApiException {
        assertNotEmpty("Organization name", orgName);
        assertNotEmpty("Store name", storeName);
        assertNotEmpty("Item path", itemPath);

        log.info("Getting item '{}' (org='{}', store='{}')", itemPath, orgName, storeName);

        // we need to deserialize the response using Jackson instead of GSON to avoid
        // differences between two libraries (e.g. deserialization of integers/decimals)
        // hence we're using custom "request" method instead of the standard swagger-codegen client
        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () ->
                new JsonStoreDataApi(apiClient).getJsonStoreData(orgName, storeName, itemPath));
    }

    /**
     * Removes an existing item. Returns {@code true} if the item existed and was successfully removed.
     */
    public boolean delete(String orgName, String storeName, String itemPath) throws ApiException {
        assertNotEmpty("Organization name", orgName);
        assertNotEmpty("Store name", storeName);
        assertNotEmpty("Item path", itemPath);

        log.info("Removing item '{}' (org='{}', store='{}')", itemPath, orgName, storeName);

        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            JsonStoreDataApi api = new JsonStoreDataApi(apiClient);
            GenericOperationResult result = api.deleteJsonStoreDataItem(orgName, storeName, itemPath);
            return result != null && result.getResult() == GenericOperationResult.ResultEnum.DELETED;
        });
    }

    /**
     * Executes a named query and returns the list of results.
     */
    public List<Object> executeQuery(String orgName, String storeName, String queryName, Map<String, Object> params) throws ApiException {
        assertNotEmpty("Organization name", orgName);
        assertNotEmpty("Store name", storeName);
        assertNotEmpty("Query name", queryName);

        log.info("Executing query '{}' (org='{}', store='{}') with parameters '{}'", queryName, orgName, storeName, params);

        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            JsonStoreQueryApi api = new JsonStoreQueryApi(apiClient);
            return api.execJsonStoreQuery(orgName, storeName, queryName, params);
        });
    }

    private static void assertNotEmpty(String what, String s) {
        if (s == null || s.isEmpty()) {
            throw new IllegalArgumentException(what + " cannot be empty or null");
        }
    }
}
