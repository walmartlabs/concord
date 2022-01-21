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
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class WorkspacePolicyIT extends AbstractServerIT {

    @Test
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

        ProcessApi processApi = new ProcessApi(getApiClient());

        StartProcessResponse spr = start(input);
        ProcessEntry pe = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pe.getStatus());

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*Workspace policy violation.*", ab);
        // ---

        policyResource.createOrUpdate(new PolicyEntry().setName(policyName).setRules(readPolicy("workspacePolicy/test-policy-relaxed.json")));

        // ---

        spr = start(input);

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        ab = getLog(pir.getLogFileName());

        assertLog(".*Hello!.*", ab);
    }


    private Map<String, Object> readPolicy(String file) throws Exception {
        URL url = WorkspacePolicyIT.class.getResource(file);
        return fromJson(new File(url.toURI()));
    }
}
