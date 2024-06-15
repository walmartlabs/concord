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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.*;

public class PrincipalPermissionIT extends AbstractServerIT {


    @Test
    public void testPrincipalPermission() throws Exception {

        byte[] payload = archive(PrincipalPermissionIT.class.getResource("principalPermission").toURI());

        InventoryQueriesApi resource = new InventoryQueriesApi(getApiClient());

        String orgName = "Default";
        String inventoryName = "inventory" + randomString();
        String queryName = "query" + randomString();
        String text = "select item_path from inventory_data where item_path like '%/testPath'";

        InventoriesApi inventoryResource = new InventoriesApi(getApiClient());
        inventoryResource.createOrUpdateInventory(orgName, new InventoryEntry().name(inventoryName));

        CreateInventoryQueryResponse cqr = resource.createOrUpdateInventoryQuery(orgName, inventoryName, queryName, text);
        assertTrue(cqr.getOk());
        assertNotNull(cqr.getId());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.inventoryName", inventoryName);
        input.put("arguments.orgName", orgName);
        input.put("arguments.queryName", queryName);
        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        List<ProcessEntry> processEntryList = processApi.listSubprocesses(spr.getInstanceId(), null);
        for (ProcessEntry pe : processEntryList) {
            assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());
        }

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Done!.*", ab);
    }
}
