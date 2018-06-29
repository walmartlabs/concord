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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.*;

public class ProjectDeleteIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void test() throws Exception {
        String orgName = "Default";
        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse cpr = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setAcceptsRawPayload(true));
        assertTrue(cpr.isOk());

        // ---

        byte[] payload = archive(ProjectDeleteIT.class.getResource("simple").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pe = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());
        assertEquals(cpr.getId(), pe.getProjectId());

        // ---

        GenericOperationResult dpr = projectsApi.delete(orgName, projectName);
        assertTrue(dpr.isOk());

        try {
            projectsApi.get(orgName, projectName);
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        pe = processApi.get(spr.getInstanceId());
        assertNull(pe.getProjectId());
    }
}
