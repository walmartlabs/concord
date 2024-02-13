package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class KvPolicyIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        String policyName = "policy_" + randomString();

        PolicyApi policyResource = new PolicyApi(getApiClient());
        policyResource.createOrUpdatePolicy(new PolicyEntry().name(policyName).rules(readPolicy("kvPolicy/test-policy.json")));
        policyResource.linkPolicy(policyName, new PolicyLinkEntry().orgName(orgName));

        // ---

        byte[] payload = archive(KvPolicyIT.class.getResource("kvPolicy").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);


        StartProcessResponse spr = start(input);
        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pe.getStatus());

        byte[] ab = getLog(pe.getInstanceId());
        assertLog(".*kv\\.putLong\\('two', 444\\).*Found KV policy violations: Maximum KV entries exceeded: current 1, limit 1.*", ab);
        // ---

        policyResource.createOrUpdatePolicy(new PolicyEntry().name(policyName).rules(readPolicy("kvPolicy/test-policy-relaxed.json")));

        // ---

        spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        ab = getLog(pir.getInstanceId());

        assertLog(".*Hello!.*", ab);
    }


    private Map<String, Object> readPolicy(String file) throws Exception {
        URL url = KvPolicyIT.class.getResource(file);
        return fromJson(new File(url.toURI()));
    }
}
