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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.*;

public class ProjectDeleteIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        String orgName = "Default";
        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse cpr = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
        assertTrue(cpr.getOk());

        // ---

        byte[] payload = archive(ProjectDeleteIT.class.getResource("simple").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());
        assertEquals(cpr.getId(), pe.getProjectId());

        // ---

        GenericOperationResult dpr = projectsApi.deleteProject(orgName, projectName);
        assertTrue(dpr.getOk());

        try {
            projectsApi.getProject(orgName, projectName);
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        pe = processApi.getProcess(spr.getInstanceId(), Collections.emptySet());
        assertNull(pe.getProjectId());
    }
}
