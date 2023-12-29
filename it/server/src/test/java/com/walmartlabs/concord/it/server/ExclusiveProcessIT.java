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
import com.walmartlabs.concord.client2.ProcessEntry.StatusEnum;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExclusiveProcessIT extends AbstractServerIT {

    @Test
    public void testExclusiveCancelOld() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        byte[] payload = archive(ExclusiveProcessIT.class.getResource("exclusiveCancelOld").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);
        input.put("arguments.time", "60000");

        StartProcessResponse spr1 = start(input);

        input.put("arguments.time", "1");
        StartProcessResponse spr2 = start(input);

        ProcessEntry p1 = waitForStatus(getApiClient(), spr1.getInstanceId(), StatusEnum.CANCELLED);
        ProcessEntry p2 = waitForStatus(getApiClient(), spr2.getInstanceId(), StatusEnum.FINISHED);

        System.out.println("p1: createdAt: " + p1.getCreatedAt() + ", status: " + p1.getStatus());
        System.out.println("p2: createdAt: " + p2.getCreatedAt() + ", status: " + p2.getStatus());
        if (p1.getStatus() != StatusEnum.CANCELLED) {
            List<ProcessStatusHistoryEntry> p1History = processApi.getStatusHistory(p1.getInstanceId());
            List<ProcessStatusHistoryEntry> p2History = processApi.getStatusHistory(p2.getInstanceId());

            System.out.println("p1 history: " + p1History);
            System.out.println("p2 history: " + p2History);
            System.out.println("p1 log:" + new String(getLog(p1.getInstanceId())));
            System.out.println("p2 log:" + new String(getLog(p2.getInstanceId())));
        }

        assertTrue(p1.getCreatedAt().isEqual(p2.getCreatedAt()) || p1.getCreatedAt().isBefore(p2.getCreatedAt()));
        assertEquals(StatusEnum.CANCELLED, p1.getStatus());
        assertEquals(StatusEnum.FINISHED, p2.getStatus());
    }
}
