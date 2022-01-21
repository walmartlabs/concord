package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.client.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;

public class InventoryIT extends AbstractServerIT {

    @Test
    public void testQuery() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        String inventoryName = "inventory_" + randomString();
        InventoriesApi inventoriesApi = new InventoriesApi(getApiClient());
        inventoriesApi.createOrUpdate(orgName, new InventoryEntry().setName(inventoryName)
                .setOrgName(orgName)
                .setVisibility(InventoryEntry.VisibilityEnum.PUBLIC));

        String queryName = "query_" + randomString();
        InventoryQueriesApi queriesApi = new InventoryQueriesApi(getApiClient());
        queriesApi.createOrUpdate(orgName, inventoryName, queryName, "select cast(to_json(item_data) as varchar) from inventory_data where item_path like '%/testPath'");

        InventoryDataApi dataApi = new InventoryDataApi(getApiClient());
        dataApi.data(orgName, inventoryName, "/testPath", "{\"data\": \"testData\"}");

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("inventoryQuery").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("arguments.inventoryName", inventoryName);
        input.put("arguments.queryName", queryName);

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Inventory Item_data: testData.*", ab);
    }
}
