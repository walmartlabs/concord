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

import com.walmartlabs.concord.client.CreateInventoryQueryResponse;
import com.walmartlabs.concord.client.InventoriesApi;
import com.walmartlabs.concord.client.InventoryEntry;
import com.walmartlabs.concord.client.InventoryQueriesApi;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class InventoryQueryIT extends AbstractServerIT {

    @Test(timeout = 60000)
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
}
