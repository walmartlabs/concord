package com.walmartlabs.concord.it.runtime.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static com.walmartlabs.concord.it.runtime.v2.ITConstants.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public class JsonStoreIT {

    @RegisterExtension
    public final ConcordRule concord = ConcordConfiguration.configure();

    /**
     * Tests various methods of the 'jsonStore' plugin.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void test() throws Exception {
        ApiClient apiClient = concord.apiClient();

        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(apiClient);
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(apiClient);
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.OWNERS));

        String storeName = "store_" + randomString();

        // ---

        Payload payload = new Payload()
                .org(orgName)
                .project(projectName)
                .archive(ProcessIT.class.getResource("jsonStore").toURI())
                .arg("storeName", storeName);

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        proc.assertLog(".*the store doesn't exist.*");
        proc.assertLog(".*the item doesn't exist.*");
        proc.assertLog(".*the store exists now.*");
        proc.assertLog(".*the item exists now.*");

        proc.assertLog(".*empty: ==.*");
        proc.assertLog(".*get: \\{x=1}.*");
        proc.assertLog(".*Updating item 'test2'.*" + orgName + ".*" + storeName + ".*");

        // ---

        JsonStoreDataApi jsonStoreDataApi = new JsonStoreDataApi(apiClient);
        Object test = jsonStoreDataApi.get(orgName, storeName, "test2");
        assertNotNull(test);
        assertTrue(test instanceof Map);

        Map<String, Object> m = (Map<String, Object>) test;
        assertEquals(m.get("x"), "1");
    }
}
