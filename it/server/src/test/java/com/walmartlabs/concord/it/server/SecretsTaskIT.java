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

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class
SecretsTaskIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String orgName = "Default";
        String projectName1 = "project_"+ randomString();
        String projectName2 = "project_" + randomString();

        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName2).rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName1).rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        String secretName = "secret_" + randomString();

        byte[] payload = archive(SecretsTaskIT.class.getResource("secretsTask").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName1);
        input.put("arguments.secretName", secretName);
        input.put("arguments.projectName1", projectName1);
        input.put("arguments.projectName2", projectName2);
        input.put("arguments.orgName", orgName);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertNotNull(pir.getLogFileName());

        byte[] bytes = getLog(pir.getInstanceId());
        // System.out.println(new String(bytes));
        assertLog(".* Delete secret2.*", bytes);
    }
}
