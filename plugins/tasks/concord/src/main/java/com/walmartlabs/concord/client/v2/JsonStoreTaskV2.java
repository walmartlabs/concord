package com.walmartlabs.concord.client.v2;

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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client.JsonStoreTaskCommon;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Map;

@Named("jsonStore")
@SuppressWarnings("unused")
public class JsonStoreTaskV2 implements Task {

    private final JsonStoreTaskCommon delegate;
    private final String processOrg;

    @Inject
    public JsonStoreTaskV2(ApiClient apiClient, Context context) {
        this.delegate = new JsonStoreTaskCommon(apiClient);
        this.processOrg = context.processConfiguration().projectInfo().orgName();
    }

    public boolean isStoreExists(String storeName) throws Exception {
        return isStoreExists(assertOrg(processOrg), storeName);
    }

    public boolean isStoreExists(String orgName, String storeName) throws Exception {
        return delegate.isStoreExists(orgName, storeName);
    }

    public boolean isExists(String storeName, String itemPath) throws Exception {
        return isExists(assertOrg(processOrg), storeName, itemPath);
    }

    public boolean isExists(String orgName, String storeName, String itemPath) throws Exception {
        return delegate.isExists(orgName, storeName, itemPath);
    }

    public void upsert(String storeName, String itemPath, Object data) throws Exception {
        upsert(assertOrg(processOrg), storeName, itemPath, data);
    }

    public void upsert(String orgName, String storeName, String itemPath, Object data) throws Exception {
        delegate.put(orgName, storeName, itemPath, data, true);
    }

    public void put(String storeName, String itemPath, Object data) throws Exception {
        put(assertOrg(processOrg), storeName, itemPath, data);
    }

    public void put(String orgName, String storeName, String itemPath, Object data) throws Exception {
        delegate.put(orgName, storeName, itemPath, data, false);
    }

    public Object get(String storageName, String itemPath) throws Exception {
        return get(assertOrg(processOrg), storageName, itemPath);
    }

    public Object get(String orgName, String storeName, String itemPath) throws Exception {
        return delegate.get(orgName, storeName, itemPath);
    }

    public Object delete(String storeName, String itemPath) throws Exception {
        return delete(assertOrg(processOrg), storeName, itemPath);
    }

    public boolean delete(String orgName, String storeName, String itemPath) throws Exception {
        return delegate.delete(orgName, storeName, itemPath);
    }

    public void upsertQuery(String storeName, String queryName, String queryText) throws Exception {
        upsertQuery(assertOrg(processOrg), storeName, queryName, queryText);
    }

    public void upsertQuery(String orgName, String storeName, String queryName, String queryText) throws Exception {
        delegate.createOrUpdateQuery(orgName, storeName, queryName, queryText);
    }

    public List<Object> executeQuery(String storeName, String queryName) throws Exception {
        return executeQuery(storeName, queryName, (Map<String, Object>) null);
    }

    public List<Object> executeQuery(String orgName, String storeName, String queryName) throws Exception {
        return executeQuery(orgName, storeName, queryName, null);
    }

    public List<Object> executeQuery(String storeName, String queryName, Map<String, Object> params) throws Exception {
        return executeQuery(assertOrg(processOrg), storeName, queryName, params);
    }

    public List<Object> executeQuery(String orgName, String storeName, String queryName, Map<String, Object> params) throws Exception {
        return delegate.executeQuery(orgName, storeName, queryName, params);
    }

    private static String assertOrg(String org) {
        if (org != null) {
            return org;
        }

        throw new RuntimeException("Can't determine the current organization name. " +
                "Please specify it explicitly or run your process in a project.");
    }
}
