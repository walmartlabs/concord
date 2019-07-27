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

import com.walmartlabs.concord.client.*;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.fail;

public class WorkspacePolicyIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void test() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        String policyName = "policy_" + randomString();

        PolicyApi policyResource = new PolicyApi(getApiClient());
        policyResource.createOrUpdate(new PolicyEntry().setName(policyName).setRules(readPolicy("workspacePolicy/test-policy.json")));
        policyResource.link(policyName, new PolicyLinkEntry().setOrgName(orgName));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("workspacePolicy").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);

        try {
            start(input);
            fail("Should fail");
        } catch (Exception e) {
        }

        // ---

        policyResource.createOrUpdate(new PolicyEntry().setName(policyName).setRules(readPolicy("workspacePolicy/test-policy-relaxed.json")));

        // ---

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello!.*", ab);
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> readPolicy(String file) throws Exception {
        URL url = WorkspacePolicyIT.class.getResource(file);
        return fromJson(new File(url.toURI()));
    }
}
