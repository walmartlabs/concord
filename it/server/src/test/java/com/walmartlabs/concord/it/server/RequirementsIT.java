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

import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// requires an agent with custom "capabilities" configured
class RequirementsIT extends AbstractServerIT {

    @BeforeAll
    static void setUp() {
        assumeTrue(System.getenv("IT_CUSTOM_AGENTS") != null);
    }

    @Test
    void testRequirementsRegex() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        byte[] payload = archive(RequirementsIT.class.getResource("processRequirements").toURI());
        Map<String, Object> input = Map.of(
                "archive", payload,
                "org", orgName,
                "project", projectName
        );

        StartProcessResponse spr = start(input);

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertNotNull(pe.getRequirements());
        assertFalse(pe.getRequirements().isEmpty());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        byte[] ab = getLog(pe.getInstanceId());
        assertLog(".*Hello from a process with requirements.*", ab);
    }

    @Test
    void testRequirementsInvalidRegex() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        byte[] payload = archive(RequirementsIT.class.getResource("processRequirements").toURI());
        Map<String, Object> input = Map.of(
                "archive", payload,
                "org", orgName,
                "project", projectName,
                "activeProfiles", "invalidRegex"
        );

        StartProcessResponse spr = start(input);

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertNotNull(pe.getRequirements());
        assertFalse(pe.getRequirements().isEmpty());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pe.getStatus());

        // ---

        byte[] ab = getLog(pe.getInstanceId());
        assertLog(".*Invalid regex in requested agent capabilities.*", ab);
    }

    @Test
    void testForkWithRequirements() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        byte[] payload = archive(RequirementsIT.class.getResource("concordTaskForkWithRequirements").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse parentSpr = start(input);

        ProcessEntry pe = waitForCompletion(getApiClient(), parentSpr.getInstanceId());
        assertNotNull(pe.getRequirements());
        assertFalse(pe.getRequirements().isEmpty());

        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        ProcessEntry processEntry = processApi.getProcess(parentSpr.getInstanceId(), Collections.singleton("childrenIds"));
        assertEquals(1, processEntry.getChildrenIds().size());

        ProcessEntry child = processApi.getProcess(processEntry.getChildrenIds().iterator().next(), Collections.emptySet());
        assertNotNull(child);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, child.getStatus());

        // ---

        byte[] ab = getLog(child.getInstanceId());
        assertLog(".*Hello from a subprocess.*", ab);

        // ---
        assertNotNull(child.getRequirements());
        assertEquals(pe.getRequirements(), child.getRequirements());
    }
}
