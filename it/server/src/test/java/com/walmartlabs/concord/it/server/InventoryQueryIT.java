package com.walmartlabs.concord.it.server;

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
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;

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
        inventoryResource.createOrUpdateInventory(orgName, new InventoryEntry().name(inventoryName));

        CreateInventoryQueryResponse cqr = resource.createOrUpdateInventoryQuery(orgName, inventoryName, queryName, text);
        assertTrue(cqr.getOk());
        assertNotNull(cqr.getId());

        List<Object> resp = resource.executeInventoryQuery(orgName, inventoryName, queryName, new HashMap<>());
        assertNotNull(resp);
    }

    @Test
    public void testDifferentContentTypes() throws Exception {
        ApiClient client = getApiClient();

        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        organizationsApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName));

        InventoriesApi inventoriesApi = new InventoriesApi(getApiClient());

        String inventoryName = "inventory_" + randomString();
        inventoriesApi.createOrUpdateInventory(orgName, new InventoryEntry()
                .name(inventoryName));

        // ---

        assertQuery(client, orgName, inventoryName, "q1_" + randomString(), "text/plain");
        assertQuery(client, orgName, inventoryName, "q2_" + randomString(), "application/json");
    }

    private static void assertQuery(ApiClient client, String orgName, String inventoryName, String queryName, String contentType) throws Exception {
        String data = "select * from inventory_data";

        HttpRequest.Builder requestBuilder = client.requestBuilder();

        String localVarPath = "/api/v1/org/{orgName}/inventory/{inventoryName}/query/{queryName}"
                .replace("{orgName}", ApiClient.urlEncode(orgName))
                .replace("{inventoryName}", ApiClient.urlEncode(inventoryName))
                .replace("{queryName}", ApiClient.urlEncode(queryName));

        requestBuilder.uri(URI.create(client.getBaseUri() + localVarPath));

        requestBuilder.header("Content-Type", contentType);
        String acceptHeaderValue = "application/json";
        acceptHeaderValue += ",application/vnd.concord-validation-errors-v1+json";
        requestBuilder.header("Accept", acceptHeaderValue);
        requestBuilder.method("POST", HttpRequest.BodyPublishers.ofString(data));

        HttpResponse<InputStream> response = client.getHttpClient().send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofInputStream());

        assertEquals(200, response.statusCode());

        new InventoryQueriesApi(client).createOrUpdateInventoryQuery(orgName, inventoryName, queryName, data);
    }
}
