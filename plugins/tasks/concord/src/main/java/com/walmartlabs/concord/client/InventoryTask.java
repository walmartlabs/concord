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
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.InjectVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named("inventory")
public class InventoryTask extends AbstractConcordTask {

    private static final Logger log = LoggerFactory.getLogger(InventoryTask.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    public Map<String, Object> ansible(@InjectVariable("context") Context ctx,
                                       String inventoryName,
                                       String hostGroupName, String queryName, Map<String, Object> params) throws Exception {

        String orgName = getOrg(ctx);
        return ansible(ctx, orgName, inventoryName, hostGroupName, queryName, params, null);
    }

    public Map<String, Object> ansible(@InjectVariable("context") Context ctx,
                                       String orgName, String inventoryName,
                                       String hostGroupName, String queryName, Map<String, Object> params) throws Exception {

        return ansible(ctx, orgName, inventoryName, hostGroupName, queryName, params, null);
    }

    public Map<String, Object> ansible(@InjectVariable("context") Context ctx,
                                       String orgName, String inventoryName,
                                       String hostGroupName, String queryName) throws Exception {

        return ansible(ctx, orgName, inventoryName, hostGroupName, queryName, null, null);
    }

    public Map<String, Object> ansible(@InjectVariable("context") Context ctx,
                                       String inventoryName,
                                       String hostGroupName, String queryName) throws Exception {

        String orgName = getOrg(ctx);
        return ansible(ctx, orgName, inventoryName, hostGroupName, queryName, null, null);
    }

    public Map<String, Object> ansible(@InjectVariable("context") Context ctx,
                                       String orgName, String inventoryName,
                                       String hostGroupName, String queryName, Map<String, Object> params,
                                       Map<String, Object> vars) throws Exception {

        List<Object> data = execQuery(ctx, orgName, inventoryName, queryName, params);

        Map<String, Object> hostVars = toHostVars(data);

        Map<String, Object> meta = new HashMap<>();
        meta.put("hostvars", hostVars);

        Map<String, Object> result = new HashMap<>();
        result.put("_meta", meta);

        Map<String, Object> hostGroup = new HashMap<>();
        hostGroup.put("hosts", hostVars.keySet());
        if (vars != null && !vars.isEmpty()) {
            hostGroup.put("vars", vars);
        }

        result.put(hostGroupName, hostGroup);

        log.info("ansible ['{}', '{}', '{}', '{}', '{}'] -> done", orgName, inventoryName, hostGroupName, queryName, params);

        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toHostVars(List<Object> data) {
        if (data == null || data.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new HashMap<>();

        for (Object o : data) {
            if (o instanceof String) {
                result.put((String) o, Collections.emptyMap());
            } else if (o instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) o;
                Object h = m.get("host");
                if (h instanceof String) {
                    m.remove("host");
                    result.put((String) h, m);
                } else {
                    throw new IllegalArgumentException("Expected a record with a \"host\" value, got " + o);
                }
            } else {
                throw new IllegalArgumentException("Expected a string value or a record, got " + o);
            }
        }

        return result;
    }

    public List<Object> query(@InjectVariable("context") Context ctx,
                              String inventoryName, String queryName, Map<String, Object> params) throws Exception {

        String orgName = getOrg(ctx);
        return query(ctx, orgName, inventoryName, queryName, params);
    }

    public List<Object> query(@InjectVariable("context") Context ctx,
                              String orgName, String inventoryName, String queryName, Map<String, Object> params) throws Exception {

        List<Object> result = execQuery(ctx, orgName, inventoryName, queryName, params);

        log.info("query ['{}', '{}', '{}', '{}'] -> {}", orgName, inventoryName, queryName, params, result != null);

        return result;
    }

    private List<Object> execQuery(Context ctx, String orgName, String inventoryName, String queryName, Map<String, Object> params) throws Exception {
        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> withClient(ctx, client -> {
            InventoryQueriesApi api = new InventoryQueriesApi(client);
            return api.executeInventoryQuery(orgName, inventoryName, queryName, params);
        }));
    }

    @SuppressWarnings("unchecked")
    private static String getOrg(Context ctx) {
        Map<String, Object> m = (Map<String, Object>) ctx.getVariable(Constants.Request.PROJECT_INFO_KEY);
        if (m == null) {
            return null;
        }

        return (String) m.get("orgName");
    }
}
