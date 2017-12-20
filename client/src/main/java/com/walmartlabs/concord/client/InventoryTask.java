package com.walmartlabs.concord.client;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.server.api.org.inventory.InventoryQueryResource;
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

    public Map<String, Object> ansible(@InjectVariable("context") Context ctx,
                                       String orgName, String inventoryName,
                                       String hostGroupName, String queryName, Map<String, Object> params) throws Exception {
        return ansible(ctx, orgName, inventoryName, hostGroupName, queryName, params, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> ansible(@InjectVariable("context") Context ctx,
           String orgName, String inventoryName,
                                       String hostGroupName, String queryName, Map<String, Object> params, Map<String, Object> vars) throws Exception {

        List<Object> hostIps = execQuery(ctx, orgName, inventoryName, queryName, params);
        Map<String, Object> hostVars = new HashMap<>();
        hostVars.put("hosts", hostIps);
        if (vars != null && !vars.isEmpty()) {
            hostVars.put("vars", vars);
        }

        log.info("ansible ['{}', '{}', '{}', '{}', '{}'] -> {}", orgName, inventoryName, hostGroupName, queryName, params, hostIps != null);

        return Collections.singletonMap(hostGroupName, hostVars);
    }

    @SuppressWarnings("unchecked")
    public List<Object> query(@InjectVariable("context") Context ctx,
            String orgName, String inventoryName, String queryName, Map<String, Object> params) throws Exception {

        List<Object> result = execQuery(ctx, orgName, inventoryName, queryName, params);

        log.info("query ['{}', '{}', '{}', '{}'] -> {}", orgName, inventoryName, queryName, params, result != null);

        return result;
    }

    private List<Object> execQuery(Context ctx, String orgName, String inventoryName, String queryName, Map<String, Object> params) throws Exception {
        return withClient(ctx, target -> {
            InventoryQueryResource proxy = target.proxy(InventoryQueryResource.class);
            return proxy.exec(orgName, inventoryName, queryName, params);
        });
    }
}
