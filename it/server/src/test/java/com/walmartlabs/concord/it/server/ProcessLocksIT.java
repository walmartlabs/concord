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
import com.walmartlabs.concord.client.ProcessEntry.StatusEnum;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProcessLocksIT extends AbstractServerIT {

    @Test
    public void testOrgScope() throws Exception {
        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();

        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry().setName(projectName)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("processLocks").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse sprA = start(input);

        ProcessEntry pirA = waitForStatus(processApi, sprA.getInstanceId(), StatusEnum.FAILED, StatusEnum.RUNNING);
        assertEquals(StatusEnum.RUNNING, pirA.getStatus());
        waitForLog(pirA.getLogFileName(), ".*locked!.*");

        // ---

        input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);

        StartProcessResponse sprB = start(input);

        ProcessEntry pirB = waitForStatus(processApi, sprB.getInstanceId(), StatusEnum.FAILED, StatusEnum.SUSPENDED);
        assertEquals(StatusEnum.SUSPENDED, pirB.getStatus());

        // ---

        pirA = waitForStatus(processApi, sprA.getInstanceId(), StatusEnum.FAILED, StatusEnum.FINISHED);
        assertEquals(StatusEnum.FINISHED, pirA.getStatus());

        pirB = waitForStatus(processApi, sprB.getInstanceId(), StatusEnum.FAILED, StatusEnum.FINISHED);
        assertEquals(StatusEnum.FINISHED, pirB.getStatus());
    }
}
