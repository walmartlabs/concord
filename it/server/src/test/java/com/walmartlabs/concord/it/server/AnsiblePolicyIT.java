package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;

public class AnsiblePolicyIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void testTaskDeny() throws Exception {
        // ---

        String orgName = "Default";
        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setAcceptsRawPayload(true));

        // ---

        String policyName = "test_policy_" + randomString();
        PolicyEntry policy = new PolicyEntry()
                .setName(policyName)
                .setRules(readPolicy("ansiblePolicyTaskDeny/test-policy.json"));

        PolicyApi policyApi = new PolicyApi(getApiClient());
        policyApi.createOrUpdate(policy);
        policyApi.link(policyName, new PolicyLinkEntry()
                .setOrgName(orgName)
                .setProjectName(projectName));

        URI dir = AnsiblePolicyIT.class.getResource("ansiblePolicyTaskDeny").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(orgName, projectName, null, null, payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Task 'Copy a local file' is forbidden by the task policy.*", ab);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPolicy(String file) throws Exception {
        URL url = AnsiblePolicyIT.class.getResource(file);
        return fromJson(new File(url.toURI()));
    }
}
