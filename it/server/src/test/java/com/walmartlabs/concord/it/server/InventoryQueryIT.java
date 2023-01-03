package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.squareup.okhttp.Call;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiResponse;
import com.walmartlabs.concord.client.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryQueryIT extends AbstractServerIT {

    @Test
    public void testQueryWithEmptyParams() throws Exception {
        InventoryQueriesApi resource = new InventoryQueriesApi(getApiClient());

        String orgName = "Default";
        String inventoryName = "inventory" + randomString();
        String queryName = "query" + randomString();
        String text = "SELECT CAST(json_build_object('host', item_data->'host', 'ansible_host', item_data->'ip', " +
                "'ooInstanceName', item_data->'ooInstanceName', 'type', item_data->'type', 'profile', " +
                "item_data->'profile', 'zone', item_data->'zone', 'clusterInventoryRef', " +
                "a.item_data->'clusterInventoryRef') AS varchar) " +
                "FROM inventory_data a " +
                "WHERE item_data @> ?::jsonb";

        InventoriesApi inventoryResource = new InventoriesApi(getApiClient());
        inventoryResource.createOrUpdate(orgName, new InventoryEntry().setName(inventoryName));

        CreateInventoryQueryResponse cqr = resource.createOrUpdate(orgName, inventoryName, queryName, text);
        assertTrue(cqr.isOk());
        assertNotNull(cqr.getId());

        List<Object> resp = resource.exec(orgName, inventoryName, queryName, new HashMap<>());
        assertNotNull(resp);
    }

    @Test
    public void testDifferentContentTypes() throws Exception {
        ApiClient client = getApiClient();

        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        organizationsApi.createOrUpdate(new OrganizationEntry()
                .setName(orgName));

        InventoriesApi inventoriesApi = new InventoriesApi(getApiClient());

        String inventoryName = "inventory_" + randomString();
        inventoriesApi.createOrUpdate(orgName, new InventoryEntry()
                .setName(inventoryName));

        // ---

        assertQuery(client, orgName, inventoryName, "q1_" + randomString(), "text/plain");
        assertQuery(client, orgName, inventoryName, "q2_" + randomString(), "application/json");
    }

    private static void assertQuery(ApiClient client, String orgName, String inventoryName, String queryName, String contentType) throws Exception {
        Set<String> auths = client.getAuthentications().keySet();
        String[] authNames = auths.toArray(new String[0]);

        String data = "select * from inventory_data";
        Map<String, String> headerParams = new HashMap<>(Collections.singletonMap("Content-Type", contentType));

        Call call = client.buildCall("/api/v1/org/" + orgName + "/inventory/" + inventoryName + "/query/" + queryName,
                "POST", new ArrayList<>(), new ArrayList<>(),
                data, headerParams, new HashMap<>(), authNames, null);

        ApiResponse<Object> response = client.execute(call, CreateInventoryQueryResponse.class);
        assertEquals(200, response.getStatusCode());
    }
}
