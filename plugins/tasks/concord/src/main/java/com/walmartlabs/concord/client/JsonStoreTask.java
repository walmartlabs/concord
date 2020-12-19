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

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.ProjectInfo;

import javax.inject.Named;
import java.util.List;
import java.util.Map;

@Named("jsonStore")
public class JsonStoreTask extends AbstractConcordTask {

    public boolean isStoreExists(@InjectVariable("context") Context ctx, String storeName) throws Exception {
        return isStoreExists(ctx, assertOrg(ctx), storeName);
    }

    public boolean isStoreExists(@InjectVariable("context") Context ctx, String orgName, String storeName) throws Exception {
        return withClient(ctx, client -> new JsonStoreTaskCommon(client).isStoreExists(orgName, storeName));
    }

    public boolean isExists(@InjectVariable("context") Context ctx, String storeName, String itemPath) throws Exception {
        return isExists(ctx, assertOrg(ctx), storeName, itemPath);
    }

    public boolean isExists(@InjectVariable("context") Context ctx, String orgName, String storeName, String itemPath) throws Exception {
        return withClient(ctx, client -> new JsonStoreTaskCommon(client).isExists(orgName, storeName, itemPath));
    }

    public void upsert(@InjectVariable("context") Context ctx, String storeName, String itemPath, Object data) throws Exception {
        upsert(ctx, assertOrg(ctx), storeName, itemPath, data);
    }

    public void upsert(@InjectVariable("context") Context ctx, String orgName, String storeName, String itemPath, Object data) throws Exception {
        withClient(ctx, client -> {
            new JsonStoreTaskCommon(client).put(orgName, storeName, itemPath, data, true);
            return null;
        });
    }

    public void put(@InjectVariable("context") Context ctx, String storeName, String itemPath, Object data) throws Exception {
        put(ctx, assertOrg(ctx), storeName, itemPath, data);
    }

    public void put(@InjectVariable("context") Context ctx, String orgName, String storeName, String itemPath, Object data) throws Exception {
        withClient(ctx, client -> {
            new JsonStoreTaskCommon(client).put(orgName, storeName, itemPath, data, false);
            return null;
        });
    }

    public Object get(@InjectVariable("context") Context ctx, String storageName, String itemPath) throws Exception {
        return get(ctx, assertOrg(ctx), storageName, itemPath);
    }

    public Object get(@InjectVariable("context") Context ctx, String orgName, String storeName, String itemPath) throws Exception {
        return withClient(ctx, client -> new JsonStoreTaskCommon(client)
                .get(orgName, storeName, itemPath));
    }

    public Object delete(@InjectVariable("context") Context ctx, String storeName, String itemPath) throws Exception {
        return delete(ctx, assertOrg(ctx), storeName, itemPath);
    }

    public boolean delete(@InjectVariable("context") Context ctx, String orgName, String storeName, String itemPath) throws Exception {
        return withClient(ctx, client -> new JsonStoreTaskCommon(client)
                .delete(orgName, storeName, itemPath));
    }

    public List<Object> executeQuery(@InjectVariable("context") Context ctx, String storeName, String queryName) throws Exception {
        return executeQuery(ctx, storeName, queryName, (Map<String, Object>) null);
    }

    public List<Object> executeQuery(@InjectVariable("context") Context ctx, String orgName, String storeName, String queryName) throws Exception {
        return executeQuery(ctx, orgName, storeName, queryName, null);
    }

    public List<Object> executeQuery(@InjectVariable("context") Context ctx, String storeName, String queryName, Map<String, Object> params) throws Exception {
        String orgName = assertOrg(ctx);
        return executeQuery(ctx, orgName, storeName, queryName, params);
    }

    public List<Object> executeQuery(@InjectVariable("context") Context ctx, String orgName, String storeName, String queryName, Map<String, Object> params) throws Exception {
        return withClient(ctx, client -> new JsonStoreTaskCommon(client)
                .executeQuery(orgName, storeName, queryName, params));
    }

    private static String assertOrg(Context ctx) {
        ProjectInfo projectInfo = ContextUtils.getProjectInfo(ctx);
        if (projectInfo != null) {
            return projectInfo.orgName();
        }

        throw new RuntimeException("Can't determine the current organization name. " +
                "Please specify it explicitly or run your process in a project.");
    }
}
