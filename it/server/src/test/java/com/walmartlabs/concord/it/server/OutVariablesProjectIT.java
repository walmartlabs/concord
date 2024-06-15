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
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.*;

public class OutVariablesProjectIT extends AbstractServerIT {

    @Test
    public void testOutVars() throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String orgName = "Default";
        String projectName = "project_" + System.currentTimeMillis();
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .acceptsRawPayload(true)
                .outVariablesMode(ProjectEntry.OutVariablesModeEnum.EVERYONE)
                .name(projectName));

        // ---

        byte[] payload = archive(OutVariablesProjectIT.class.getResource("example").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);
        input.put("out", "myName");
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("out", Collections.singletonList("myBool"));
        input.put("request", cfg);
        StartProcessResponse spr = start(input);

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        Map<String, Object> out = (Map<String, Object>) pe.getMeta().get("out");
        assertEquals("world", out.get("myName"));
        assertEquals(true, out.get("myBool"));
    }

    @Test
    public void testReject() throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String orgName = "Default";
        String projectName = "project_" + System.currentTimeMillis();
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .acceptsRawPayload(true)
                .name(projectName));

        // ---

        byte[] payload = archive(OutVariablesProjectIT.class.getResource("example").toURI());

        try {
            Map<String, Object> input = new HashMap<>();
            input.put("org", orgName);
            input.put("project", projectName);
            input.put("archive", payload);
            input.put("out", "myName,myBool");
            start(input);
            fail("should fail");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("The project is not accepting custom out variables"));
        }
    }

    @Test
    public void testRejectFromRequest() throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String orgName = "Default";
        String projectName = "project_" + System.currentTimeMillis();
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .acceptsRawPayload(true)
                .name(projectName));

        // ---
        byte[] payload = archive(OutVariablesProjectIT.class.getResource("example").toURI());
        try {
            Map<String, Object> input = new HashMap<>();
            input.put("org", orgName);
            input.put("project", projectName);
            input.put("archive", payload);
            Map<String, Object> cfg = new HashMap<>();
            cfg.put("out", Collections.singletonList("myName"));
            input.put("request", cfg);
            start(input);

            StartProcessResponse spr = start(input);
            ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());

            assertEquals(ProcessEntry.StatusEnum.FAILED, pe.getStatus());
            byte[] ab = getLog(pe.getInstanceId());
            assertLog(".*The project is not accepting custom out variables.*", ab);

        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("The project is not accepting custom out variables"));
        }
    }
}
