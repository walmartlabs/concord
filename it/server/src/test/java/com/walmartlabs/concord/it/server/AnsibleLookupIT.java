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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnsibleLookupIT extends AbstractServerIT {

    @Test
    public void testSecrets() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String secretName = "mySecret";
        String secretValue = "value_" + randomString();
        String secretPwd = randomPwd();
        addPlainSecret(orgName, secretName, false, secretPwd, secretValue.getBytes());

        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

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

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertNoLog(".*Explicit org " + secretValue + ".*", ab);
        assertNoLog(".*Implicit org " + secretValue + ".*", ab);
        assertLogAtLeast(".*ENABLING NO_LOG.*", 2, ab);
    }

    @Test
    public void testSecretData() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String secretName = "mySecret";
        String secretValue = "value_" + randomString();
        String secretPwd = randomPwd();
        addPlainSecret(orgName, secretName, false, secretPwd, secretValue.getBytes());

        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

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

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertNoLog(".*Explicit org " + secretValue + ".*", ab);
        assertNoLog(".*Implicit org " + secretValue + ".*", ab);
        assertLogAtLeast(".*ENABLING NO_LOG.*", 2, ab);
    }

    @Test
    public void testSecretDataNoPassword() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String secretName = "mySecret";
        String secretValue = "value_" + randomString();
        addPlainSecret(orgName, secretName, false, null, secretValue.getBytes());

        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        URI dir = AnsibleLookupIT.class.getResource("ansibleLookupSecretDataNoPassword").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---
        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);
        input.put("arguments.orgName", orgName);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertNoLog(".*Explicit org " + secretValue + ".*", ab);
        assertNoLog(".*Implicit org " + secretValue + ".*", ab);
    }

    @Test
    public void testPublickey() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String secretName = "mySecret_" + randomString();
        generateKeyPair(orgName, secretName, false, null);

        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

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

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertNoLog(".*Explicit org: ssh-rsa" + ".*", ab);
        assertNoLog(".*Implicit org: ssh-rsa" + ".*", ab);
        assertLogAtLeast(".*ENABLING NO_LOG.*", 2, ab);
    }

    /**
     * Verify that {@code lookup('concord_data_secret', ...)} returns a valid value.
     */
    @Test
    public void testSecretValue() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String secretName = "mySecret_" + randomString();
        String secretValue = "hello_" + randomString();
        addPlainSecret(orgName, secretName, false, null, secretValue.getBytes());

        // ---

        byte[] payload = archive(AnsibleLookupIT.class.getResource("ansibleLookupSecretDataValue").toURI(), ITConstants.DEPENDENCIES_DIR);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.orgName", orgName);
        input.put("arguments.secretName", secretName);

        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(pe.getInstanceId());
        assertLog(".*Value: " + secretValue + ".*", ab);
    }
}
