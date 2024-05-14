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
import com.walmartlabs.concord.client2.*;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import javax.xml.bind.DatatypeConverter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CryptoIT extends AbstractServerIT {

    @Test
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

    @Test
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

    @Test
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

    @Test
    public void testExportAsFileWithOrg() throws Exception {
        String orgName = "org@" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String secretName = "secret@" + randomString();
        String secretValue = "value@" + randomString();
        String storePassword = "store1A@" + randomString();

        addPlainSecret(orgName, secretName, false, storePassword, secretValue.getBytes());

        // ---

        byte[] payload = archive(CryptoIT.class.getResource("cryptoFileWithOrg").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "archive", payload,
                "arguments.secretName", secretName,
                "arguments.pwd", storePassword,
                "arguments.secretOrgName", orgName));

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*We got " + secretValue + ".*", ab);
    }

    @Test
    public void testWithoutPassword() throws Exception {
        String orgName = "org@" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String secretName = "secret@" + randomString();
        String secretValue = "value@" + randomString();

        addPlainSecret(orgName, secretName, false, null, secretValue.getBytes());

        // ---

        byte[] payload = archive(CryptoIT.class.getResource("cryptoWithoutPassword").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "archive", payload,
                "arguments.secretName", secretName,
                "arguments.secretOrgName", orgName));

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*We got " + secretValue + ".*", ab);
    }

    @Test
    public void testEncryptString() throws Exception {
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

        String value = "value_" + randomString();

        EncryptValueResponse evr = projectsApi.encrypt(orgName, projectName, value);
        assertTrue(evr.getOk());

        // ---

        byte[] payload = archive(CryptoIT.class.getResource("encryptString").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "org", orgName,
                "project", projectName,
                "archive", payload,
                "arguments.decryptedValue", value));

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*We got " + Pattern.quote(evr.getData()) + ".*", ab);
    }

    @Test
    public void testDecryptString() throws Exception {
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

        String value = "value_" + randomString();

        EncryptValueResponse evr = projectsApi.encrypt(orgName, projectName, value);
        assertTrue(evr.getOk());

        // ---

        byte[] payload = archive(CryptoIT.class.getResource("decryptString").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "org", orgName,
                "project", projectName,
                "archive", payload,
                "arguments.encryptedValue", evr.getData()));

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*We got " + value + ".*", ab);
    }

    @Test
    public void testDecryptStringTooBig() throws Exception {
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

        String value = "value_" + randomString();

        EncryptValueResponse evr = projectsApi.encrypt(orgName, projectName, value);
        assertTrue(evr.getOk());

        // ---

        byte[] payload = archive(CryptoIT.class.getResource("decryptStringTooBig").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "org", orgName,
                "project", projectName,
                "archive", payload));

        // ---

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FAILED);

        byte[] ab = getLog(pir.getInstanceId());
        assertLogAtLeast(".*too big.*", 1, ab);
    }

    @Test
    public void testDecryptInvalidString() throws Exception {
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

        byte[] payload = archive(CryptoIT.class.getResource("decryptString").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "org", orgName,
                "project", projectName,
                "archive", payload,
                "arguments.encryptedValue", DatatypeConverter.printBase64Binary(new byte[]{0, 1, 2})));

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pir.getStatus());

        // ---

        spr = start(ImmutableMap.of(
                "org", orgName,
                "project", projectName,
                "archive", payload,
                "arguments.encryptedValue", "W+YrVH9Q0YKDZ5j8UytRAQ==")); // junk

        // ---

        pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pir.getStatus(),
                "Process logs: " + new String(getLog(pir.getInstanceId())));
    }

    @Test
    public void testProjectSecret() throws Exception {
        String orgName = "Default";

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        String secretName = "secret@" + randomString();
        String secretUsername = "username@" + randomString();
        String secretPassword = "password@" + randomString();
        String storePassword = "store1A@" + randomString();

        addUsernamePassword(orgName, projectName, secretName, false, storePassword, secretUsername, secretPassword);

        // ---

        String username = "user_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeysApi.createUserApiKey(new CreateApiKeyRequest()
                .username(username));

        // ---

        setApiKey(cakr.getKey());

        // ---

        test("cryptoPwd", secretName, storePassword, ".*secrets can only be accessed within the project they belong to.*" + secretName + ".*");
    }

    /**
     * If the organization is not specified the crypto task must pick the current organization.
     * It works because Concord provides {@code projectInfo} variable that can be used to determine
     * the current organization.
     * The test uses a form to verify that "organization auto select" works
     * for new and resumed processes.
     */
    @Test
    public void testCurrentOrg() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.OWNERS));

        String secretName = "secret_" + randomString();

        String orgSecretValue = "org value";
        addPlainSecret(orgName, secretName, false, null, orgSecretValue.getBytes());

        String defaultSecretValue = "default value";
        addPlainSecret("Default", secretName, false, null, defaultSecretValue.getBytes());

        // ---

        byte[] payload = archive(CryptoIT.class.getResource("currentOrgCrypto").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.mySecretName", secretName);
        input.put("org", orgName);
        input.put("project", projectName);
        StartProcessResponse spr = start(input);

        ProcessEntry pe = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);
        assertEquals(ProcessEntry.StatusEnum.SUSPENDED, pe.getStatus());

        // ---

        ProcessFormsApi processFormsApi = new ProcessFormsApi(getApiClient());
        List<FormListEntry> forms = processFormsApi.listProcessForms(pe.getInstanceId());
        assertEquals(1, forms.size());

        FormListEntry form = forms.get(0);
        processFormsApi.submitForm(pe.getInstanceId(), form.getName(), Collections.singletonMap("x", "123"));

        pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        byte[] ab = getLog(pe.getInstanceId());
        assertLog(".*Before form: " + orgSecretValue + ".*", ab);
        assertLog(".*After form: " + orgSecretValue + ".*", ab);
    }

    private void test(String project, String secretName, String storePassword, @Language("RegExp") String log) throws Exception {
        byte[] payload = archive(CryptoIT.class.getResource(project).toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "archive", payload,
                "arguments.secretName", secretName,
                "arguments.pwd", storePassword));

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLogAtLeast(log, 1, ab);
    }
}
