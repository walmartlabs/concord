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
import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SharedConcordExtension.class)
public class JsonStoreIT extends AbstractTest {

    static ConcordRule concord;

    @BeforeAll
    static void setUp(ConcordRule rule) {
        concord = rule;
    }

    /**
     * Tests various methods of the 'jsonStore' plugin.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void test() throws Exception {
        ApiClient apiClient = concord.apiClient();

        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(apiClient);
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(apiClient);
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.OWNERS));

        String storeName = "store_" + randomString();

        // ---

        Payload payload = new Payload()
                .org(orgName)
                .project(projectName)
                .archive(resource("jsonStore"))
                .arg("storeName", storeName);

        ConcordProcess proc = concord.processes().start(payload);

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*the store doesn't exist.*");
        proc.assertLog(".*the item doesn't exist.*");
        proc.assertLog(".*the store exists now.*");
        proc.assertLog(".*the item exists now.*");

        proc.assertLog(".*empty: ==.*");
        proc.assertLog(".*get: \\{x=1}.*");
        proc.assertLog(".*Updating item 'test2'.*" + orgName + ".*" + storeName + ".*");

        // ---

        JsonStoreDataApi jsonStoreDataApi = new JsonStoreDataApi(apiClient);
        Object test = jsonStoreDataApi.getJsonStoreData(orgName, storeName, "test2");
        assertNotNull(test);
        assertTrue(test instanceof Map);

        Map<String, Object> m = (Map<String, Object>) test;
        assertEquals(m.get("x"), "1");
    }
}
