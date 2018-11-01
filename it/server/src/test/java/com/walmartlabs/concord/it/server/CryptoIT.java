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

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.client.*;
import org.junit.Test;

import javax.xml.bind.DatatypeConverter;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CryptoIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void testPlain() throws Exception {
        String orgName = "Default";

        // ---

        String secretName = "secret@" + randomString();
        String secretValue = "value@" + randomString();
        String storePassword = "store1A@" + randomString();

        addPlainSecret(orgName, secretName, false, storePassword, secretValue.getBytes());

        // ---

        test("cryptoPlain", secretName, storePassword, ".*value=" + secretValue + ".*");
    }

    @Test(timeout = 60000)
    public void testUsernamePassword() throws Exception {
        String orgName = "Default";

        // ---

        String secretName = "secret@" + randomString();
        String secretUsername = "username@" + randomString();
        String secretPassword = "password@" + randomString();
        String storePassword = "store1A@" + randomString();

        addUsernamePassword(orgName, secretName, false, storePassword, secretUsername, secretPassword);

        // ---

        test("cryptoPwd", secretName, storePassword, ".*" + secretUsername + " " + secretPassword + ".*");
    }

    @Test(timeout = 60000)
    public void testExportAsFile() throws Exception {
        String orgName = "Default";

        // ---

        String secretName = "secret@" + randomString();
        String secretValue = "value@" + randomString();
        String storePassword = "store1A@" + randomString();

        addPlainSecret(orgName, secretName, false, storePassword, secretValue.getBytes());

        // ---

        test("cryptoFile", secretName, storePassword, ".*We got " + secretValue + ".*");
    }

    @Test(timeout = 60000)
    public void testExportAsFileWithOrg() throws Exception {
        String orgName = "org@" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String secretName = "secret@" + randomString();
        String secretValue = "value@" + randomString();
        String storePassword = "store1A@" + randomString();

        addPlainSecret(orgName, secretName, false, storePassword, secretValue.getBytes());

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("cryptoFileWithOrg").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "archive", payload,
                "arguments.secretName", secretName,
                "arguments.pwd", storePassword,
                "arguments.secretOrgName", orgName));

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*We got " + secretValue + ".*", ab);
    }

    @Test(timeout = 60000)
    public void testWithoutPassword() throws Exception {
        String orgName = "org@" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String secretName = "secret@" + randomString();
        String secretValue = "value@" + randomString();

        addPlainSecret(orgName, secretName, false, null, secretValue.getBytes());

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("cryptoWithoutPassword").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "archive", payload,
                "arguments.secretName", secretName,
                "arguments.secretOrgName", orgName));

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*We got " + secretValue + ".*", ab);
    }

    @Test(timeout = 60000)
    public void testDecryptString() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setAcceptsRawPayload(true));

        // ---

        String value = "value_" + randomString();

        EncryptValueResponse evr = projectsApi.encrypt(orgName, projectName, value);
        assertTrue(evr.isOk());

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("decryptString").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "org", orgName,
                "project", projectName,
                "archive", payload,
                "arguments.encryptedValue", evr.getData()));

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*We got " + value + ".*", ab);
    }

    @Test(timeout = 60000)
    public void testDecryptStringTooBig() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setAcceptsRawPayload(true));

        // ---

        String value = "value_" + randomString();

        EncryptValueResponse evr = projectsApi.encrypt(orgName, projectName, value);
        assertTrue(evr.isOk());

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("decryptStringTooBig").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "org", orgName,
                "project", projectName,
                "archive", payload));

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.FAILED);

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*too big.*", ab);
    }

    @Test(timeout = 60000)
    public void testDecryptInvalidString() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setAcceptsRawPayload(true));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("decryptString").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "org", orgName,
                "project", projectName,
                "archive", payload,
                "arguments.encryptedValue", DatatypeConverter.printBase64Binary(new byte[]{0, 1, 2})));

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pir.getStatus());

        // ---

        spr = start(ImmutableMap.of(
                "org", orgName,
                "project", projectName,
                "archive", payload,
                "arguments.encryptedValue", "W+YrVH9Q0YKDZ5j8UytRAQ==")); // junk

        // ---

        pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pir.getStatus());
    }

    @Test
    public void testProjectSecret() throws Exception {
        String orgName = "Default";

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setAcceptsRawPayload(true));

        // ---

        String secretName = "secret@" + randomString();
        String secretUsername = "username@" + randomString();
        String secretPassword = "password@" + randomString();
        String storePassword = "store1A@" + randomString();

        addUsernamePassword(orgName, projectName, secretName, false, storePassword, secretUsername, secretPassword);

        // ---

        String username = "user_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(username)
                .setType(CreateUserRequest.TypeEnum.LOCAL)
                .setAdmin(false));

        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeysApi.create(new CreateApiKeyRequest()
                .setUsername(username));

        // ---

        setApiKey(cakr.getKey());

        // ---

        test("cryptoPwd", secretName, storePassword, ".*Forbidden:.*" + secretName + ".*");
    }

    private void test(String project, String secretName, String storePassword, String log) throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource(project).toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "archive", payload,
                "arguments.secretName", secretName,
                "arguments.pwd", storePassword));

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(log, ab);
    }
}
