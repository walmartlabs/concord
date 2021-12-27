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
import com.walmartlabs.concord.client.ProjectEntry;
import com.walmartlabs.concord.client.ProjectsApi;
import com.walmartlabs.concord.client.StartProcessResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static org.junit.jupiter.api.Assertions.fail;

public class RawPayloadProjectIT extends AbstractServerIT {

    @Test
    public void testReject() throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String orgName = "Default";
        String projectName = "project_" + System.currentTimeMillis();
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        try {
            Map<String, Object> input = new HashMap<>();
            input.put("org", orgName);
            input.put("project", projectName);
            input.put("archive", payload);
            StartProcessResponse process = start(input);
            System.out.println("process: " + process);
            fail("should fail");
        } catch (ApiException e) {
        }
    }
}
