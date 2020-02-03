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

import com.squareup.okhttp.Request;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.ProjectInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.List;
import java.util.Map;

@Named("jsonStore")
public class JsonStoreTask extends AbstractConcordTask {

    private static final Logger log = LoggerFactory.getLogger(JsonStoreTask.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    public void put(@InjectVariable("context") Context ctx, String storeName, String itemPath, Object data) throws ApiException {
        put(ctx, assertOrg(ctx), storeName, itemPath, data);
    }

    public void put(@InjectVariable("context") Context ctx, String orgName, String storeName, String itemPath, Object data) throws ApiException {
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

        ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> withClient(ctx, client -> {
            JsonStoreDataApi api = new JsonStoreDataApi(client);
            return api.data(orgName, storeName, itemPath, data);
        }));
    }

    public Object get(@InjectVariable("context") Context ctx, String storageName, String itemPath) throws ApiException {
        return get(ctx, assertOrg(ctx), storageName, itemPath);
    }

    public Object get(@InjectVariable("context") Context ctx, String orgName, String storeName, String itemPath) throws ApiException {
        assertNotEmpty("Organization name", orgName);
        assertNotEmpty("Store name", storeName);
        assertNotEmpty("Item path", itemPath);

        log.info("Getting item '{}' (org='{}', store='{}')", itemPath, orgName, storeName);

        // we need to deserialize the response using Jackson instead of GSON to avoid
        // differences between two libraries (e.g. deserialization of integers/decimals)
        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () ->
                request(ctx, "/api/v1/org/" + orgName + "/jsonstore/" + storeName + "/item/" + itemPath, "GET", null, Map.class));
    }

    public Object delete(@InjectVariable("context") Context ctx, String storeName, String itemPath) throws ApiException {
        return delete(ctx, assertOrg(ctx), storeName, itemPath);
    }

    public boolean delete(@InjectVariable("context") Context ctx, String orgName, String storeName, String itemPath) throws ApiException {
        assertNotEmpty("Organization name", orgName);
        assertNotEmpty("Store name", storeName);
        assertNotEmpty("Item path", itemPath);

        log.info("Removing item '{}' (org='{}', store='{}')", itemPath, orgName, storeName);

        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> withClient(ctx, client -> {
            JsonStoreDataApi api = new JsonStoreDataApi(client);
            GenericOperationResult result = api.delete(orgName, storeName, itemPath);
            return result != null && result.getResult() == GenericOperationResult.ResultEnum.DELETED;
        }));
    }

    public List<Object> executeQuery(@InjectVariable("context") Context ctx, String storeName, String queryName) throws ApiException {
        return executeQuery(ctx, storeName, queryName, (Map<String, Object>) null);
    }

    public List<Object> executeQuery(@InjectVariable("context") Context ctx, String orgName, String storeName, String queryName) throws ApiException {
        return executeQuery(ctx, orgName, storeName, queryName, null);
    }

    public List<Object> executeQuery(@InjectVariable("context") Context ctx, String storeName, String queryName, Map<String, Object> params) throws ApiException {
        String orgName = assertOrg(ctx);
        return executeQuery(ctx, orgName, storeName, queryName, params);
    }

    public List<Object> executeQuery(@InjectVariable("context") Context ctx, String orgName, String storeName, String queryName, Map<String, Object> params) throws ApiException {
        assertNotEmpty("Organization name", orgName);
        assertNotEmpty("Store name", storeName);
        assertNotEmpty("Query name", queryName);

        log.info("Executing query '{}' (org='{}', store='{}') with parameters '{}'", queryName, orgName, storeName, params);

        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> withClient(ctx, client -> {
            JsonStoreQueryApi api = new JsonStoreQueryApi(client);
            return api.exec(orgName, storeName, queryName, params);
        }));
    }

    private static String assertOrg(Context ctx) {
        ProjectInfo projectInfo = ContextUtils.getProjectInfo(ctx);
        if (projectInfo != null) {
            return projectInfo.orgName();
        }

        throw new RuntimeException("Can't determine the current organization name. " +
                "Please specify it explicitly or run your process in a project.");
    }

    private static void assertNotEmpty(String what, String s) {
        if (s == null || s.isEmpty()) {
            throw new IllegalArgumentException(what + " cannot be empty or null");
        }
    }
}
