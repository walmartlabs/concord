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

import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.OrganizationResource;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.ProjectResource;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.assertEquals;

public class AnsibleLookupIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testSecrets() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationResource organizationResource = proxy(OrganizationResource.class);
        organizationResource.createOrUpdate(new OrganizationEntry(orgName));

        String secretName = "mySecret";
        String secretValue = "value_" + randomString();
        String secretPwd = "pwd_" + randomString();
        addPlainSecret(orgName, secretName, false, secretPwd, secretValue.getBytes());

        String projectName = "project_" + randomString();
        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(orgName, new ProjectEntry(projectName));

        // ---

        URI dir = AnsibleLookupIT.class.getResource("ansibleLookupSecret").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);
        input.put("arguments.orgName", orgName);
        input.put("arguments.secretPwd", secretPwd);
        StartProcessResponse spr = start(input);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertNoLog(".*Explicit org " + secretValue + ".*", ab);
        assertNoLog(".*Implicit org " + secretValue + ".*", ab);
        assertLogAtLeast(".*ENABLING NO_LOG.*", 2, ab);
    }

    @Test(timeout = 30000)
    public void testSecretData() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationResource organizationResource = proxy(OrganizationResource.class);
        organizationResource.createOrUpdate(new OrganizationEntry(orgName));

        String secretName = "mySecret";
        String secretValue = "value_" + randomString();
        String secretPwd = "pwd_" + randomString();
        addPlainSecret(orgName, secretName, false, secretPwd, secretValue.getBytes());

        String projectName = "project_" + randomString();
        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(orgName, new ProjectEntry(projectName));

        // ---

        URI dir = AnsibleLookupIT.class.getResource("ansibleLookupSecretData").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);
        input.put("arguments.orgName", orgName);
        input.put("arguments.secretPwd", secretPwd);
        StartProcessResponse spr = start(input);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertNoLog(".*Explicit org " + secretValue + ".*", ab);
        assertNoLog(".*Implicit org " + secretValue + ".*", ab);
        assertLogAtLeast(".*ENABLING NO_LOG.*", 2, ab);
    }

    @Test(timeout = 30000)
    public void testPublickey() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationResource organizationResource = proxy(OrganizationResource.class);
        organizationResource.createOrUpdate(new OrganizationEntry(orgName));

        String secretName = "mySecret_" + randomString();
        generateKeyPair(orgName, secretName, false, null);

        String projectName = "project_" + randomString();
        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(orgName, new ProjectEntry(projectName));

        // ---

        URI dir = AnsibleLookupIT.class.getResource("ansibleLookupPublicKey").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);
        input.put("arguments.orgName", orgName);
        input.put("arguments.secretName", secretName);
        StartProcessResponse spr = start(input);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertNoLog(".*Explicit org: ssh-rsa" + ".*", ab);
        assertNoLog(".*Implicit org: ssh-rsa" + ".*", ab);
        assertLogAtLeast(".*ENABLING NO_LOG.*", 2, ab);
    }


}
