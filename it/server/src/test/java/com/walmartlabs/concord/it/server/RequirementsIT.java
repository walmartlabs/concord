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
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

// requires an agent with custom "capabilities" configured
public class RequirementsIT extends AbstractServerIT {

    @BeforeClass
    public static void setUp() {
        assumeNotNull(System.getenv("IT_CUSTOM_AGENTS"));
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testForkWithRequirements() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setVisibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        byte[] payload = archive(ProcessRbacIT.class.getResource("concordTaskForkWithRequirements").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse parentSpr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForCompletion(processApi, parentSpr.getInstanceId());
        assertNotNull(pe.getRequirements());
        assertFalse(pe.getRequirements().isEmpty());

        ProcessEntry processEntry = processApi.get(parentSpr.getInstanceId());
        assertEquals(1, processEntry.getChildrenIds().size());

        ProcessEntry child = processApi.get(processEntry.getChildrenIds().get(0));
        assertNotNull(child);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, child.getStatus());

        // ---

        byte[] ab = getLog(child.getLogFileName());
        assertLog(".*Hello from a subprocess.*", ab);

        // ---
        assertNotNull(child.getRequirements());
        assertEquals(pe.getRequirements(), child.getRequirements());
    }
}
