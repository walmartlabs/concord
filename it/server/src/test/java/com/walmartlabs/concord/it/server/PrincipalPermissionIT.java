package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.api.org.inventory.*;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.org.OrganizationManager;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PrincipalPermissionIT extends AbstractServerIT {


    @Test(timeout = 60000)
    public void testPrincipalPermission() throws Exception {

        byte[] payload = archive(PrincipalPermissionIT.class.getResource("principalPermission").toURI());

        InventoryQueryResource resource = proxy(InventoryQueryResource.class);

        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        String inventoryName = "inventory" + randomString();
        String queryName = "query" + randomString();
        String text = "select item_path from inventory_data where item_path like '%/testPath'";

        InventoryResource inventoryResource = proxy(InventoryResource.class);
        inventoryResource.createOrUpdate(orgName, new InventoryEntry(inventoryName));

        CreateInventoryQueryResponse cqr = resource.createOrUpdate(orgName, inventoryName, queryName, text);
        assertTrue(cqr.isOk());
        assertNotNull(cqr.getId());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.inventoryName", inventoryName);
        input.put("arguments.orgName", orgName);
        input.put("arguments.queryName", queryName);
        StartProcessResponse spr = start(input);

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        List<ProcessEntry> processEntryList = processResource.listSubprocesses(spr.getInstanceId(), null);
        for (ProcessEntry pe : processEntryList) {
            assertEquals(ProcessStatus.FINISHED, pe.getStatus());
        }

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Done!.*", ab);
    }
}
