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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.api.org.policy.PolicyEntry;
import com.walmartlabs.concord.server.api.org.policy.PolicyLinkEntry;
import com.walmartlabs.concord.server.api.org.policy.PolicyResource;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.ProjectOperationResponse;
import com.walmartlabs.concord.server.api.org.project.ProjectResource;
import com.walmartlabs.concord.server.api.process.*;
import com.walmartlabs.concord.server.api.project.CreateProjectResponse;
import com.walmartlabs.concord.server.org.OrganizationManager;
import org.junit.Test;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.assertEquals;

public class AnsiblePolicyIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void testTaskDeny() throws Exception {
        // ---
        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        String projectName = "project_" + randomString();

        ProjectResource projectResource = proxy(ProjectResource.class);
        ProjectOperationResponse cpr = projectResource.createOrUpdate(orgName, new ProjectEntry(projectName));

        String entryPoint = projectName;

        // ---
        String policyName = "test_policy_" + randomString();
        PolicyEntry policy = new PolicyEntry(policyName, readPolicy("ansiblePolicyTaskDeny/test-policy.json"));

        PolicyResource policyResource = proxy(PolicyResource.class);
        policyResource.createOrUpdate(policy);
        policyResource.link(policyName, new PolicyLinkEntry(orgName, projectName));

        URI dir = AnsiblePolicyIT.class.getResource("ansiblePolicyTaskDeny").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---
        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(entryPoint, new ByteArrayInputStream(payload), null, false, null);

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FAILED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Task 'Copy a local file' is forbidden by the task policy.*", ab);
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> readPolicy(String file) throws IOException {
        URL url = AnsiblePolicyIT.class.getResource(file);
        return new ObjectMapper().readValue(url, Map.class);
    }
}
