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

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnsiblePolicyIT extends AbstractServerIT {

    @Test
    public void testTaskDeny() throws Exception {
        // ---

        String orgName = "Default";
        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        String policyName = "test_policy_" + randomString();
        PolicyEntry policy = new PolicyEntry()
                .name(policyName)
                .rules(readPolicy("ansiblePolicyTaskDeny/test-policy.json"));

        PolicyApi policyApi = new PolicyApi(getApiClient());
        policyApi.createOrUpdatePolicy(policy);
        policyApi.linkPolicy(policyName, new PolicyLinkEntry()
                .orgName(orgName)
                .projectName(projectName));

        URI dir = AnsiblePolicyIT.class.getResource("ansiblePolicyTaskDeny").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(orgName, projectName, null, null, payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Task 'Copy a local file \\(copy\\)' is forbidden by the task policy.*", ab);
    }

    private Map<String, Object> readPolicy(String file) throws Exception {
        URL url = AnsiblePolicyIT.class.getResource(file);
        return fromJson(new File(url.toURI()));
    }
}
