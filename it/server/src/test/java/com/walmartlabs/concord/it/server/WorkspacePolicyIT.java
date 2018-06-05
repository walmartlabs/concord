package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.OrganizationResource;
import com.walmartlabs.concord.server.api.org.policy.PolicyEntry;
import com.walmartlabs.concord.server.api.org.policy.PolicyLinkEntry;
import com.walmartlabs.concord.server.api.org.policy.PolicyResource;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.ProjectResource;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.fail;

public class WorkspacePolicyIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void test() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationResource organizationResource = proxy(OrganizationResource.class);
        organizationResource.createOrUpdate(new OrganizationEntry(orgName));

        // ---

        String projectName = "project_" + randomString();

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(orgName, new ProjectEntry(projectName));

        // ---

        String policyName = "policy_" + randomString();

        PolicyResource policyResource = proxy(PolicyResource.class);
        policyResource.createOrUpdate(new PolicyEntry(policyName, readPolicy("workspacePolicy/test-policy.json")));
        policyResource.link(policyName, new PolicyLinkEntry(orgName));

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

        policyResource.createOrUpdate(new PolicyEntry(policyName, readPolicy("workspacePolicy/test-policy-relaxed.json")));

        // ---

        StartProcessResponse spr = start(input);

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello!.*", ab);
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> readPolicy(String file) throws IOException {
        URL url = WorkspacePolicyIT.class.getResource(file);
        return new ObjectMapper().readValue(url, Map.class);
    }
}
