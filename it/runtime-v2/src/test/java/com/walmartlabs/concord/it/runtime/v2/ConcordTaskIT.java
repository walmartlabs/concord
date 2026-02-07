package com.walmartlabs.concord.it.runtime.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

public class ConcordTaskIT extends AbstractTest {

    static ConcordRule concord;

    @BeforeAll
    static void setUp(ConcordRule rule) {
        concord = rule;
    }

    /**
     * Test for concord/project-task
     */
    @Test
    public void testCreateProject() throws Exception {
        String projectName = "project_" + randomString();

        Payload payload = new Payload()
                .archive(resource("concord/projectTask"))
                .arg("newProjectName", projectName);

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---
        proc.assertLog(".*Done!.*");
    }

    /**
     * start process with api key
     */
    @Test
    public void testExternalApiToken() throws Exception {
        String username = "user_" + randomString();

        UsersApi usersApi = new UsersApi(concord.apiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeysApi = new ApiKeysApi(concord.apiClient());
        CreateApiKeyResponse cakr = apiKeysApi.createUserApiKey(new CreateApiKeyRequest()
                .username(username));

        // ---
        Payload payload = new Payload()
                .archive(resource("concord/concordTaskApiKey"))
                .arg("myApiKey", cakr.getKey());

        ConcordProcess proc = concord.processes().start(payload);

        // ---
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---
        proc.assertLog(".*Hello, Concord!. From: .*" + username + ".*");
    }

    @Test
    public void testSuspendParentProcess() throws Exception {
        Payload payload = new Payload()
                .archive(resource("concord/concordTaskSuspendParentProcess"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---
        proc.assertLog(".*Hello, Concord!.*");
    }

    @Test
    public void testForkWithArguments() throws Exception {
        String orgName = "org_" + randomString();
        concord.organizations().create(orgName);

        String projectName = "project_" + randomString();
        concord.projects().create(orgName, projectName);

        Payload payload = new Payload()
                .archive(resource("concord/concordTaskForkWithArguments"))
                .org(orgName)
                .project(projectName);

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);
        ProcessEntry processEntry = proc.getEntry("childrenIds");

        // ---
        assertEquals(1, processEntry.getChildrenIds().size());

        ConcordProcess child = concord.processes().get(processEntry.getChildrenIds().iterator().next());

        // ---
        assertNotNull(child);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, child.getEntry().getStatus());

        // ---

        child.assertLog(".*Hello from a subprocess.*");
        child.assertLog(".*Concord Fork Process 123.*");
    }

    @Test
    public void testForkSuspend() throws Exception {
        String nameVar = "name_" + randomString();

        String orgName = "org_" + randomString();
        concord.organizations().create(orgName);

        String projectName = "project_" + randomString();
        concord.projects().create(orgName, projectName);

        Payload payload = new Payload()
                .archive(resource("concord/concordTaskForkSuspend"))
                .org(orgName)
                .project(projectName)
                .arg("name", nameVar);

        ConcordProcess proc = concord.processes().start(payload);

        // ---
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*\\{varFromFork=Hello, " + nameVar + "}.*");
        proc.assertLog(".*\\{varFromFork=Bye, " + nameVar + "}.*");
    }

    @Test
    public void testSubprocessIgnoreFail() throws Exception {
        Payload payload = new Payload()
                .archive(resource("concord/concordSubIgnoreFail"));

        ConcordProcess proc = concord.processes().start(payload);

        // ---
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---
        proc.assertLog(".*Done!.*");
    }

    @Test
    public void testOutVarsNotFound() throws Exception {
        Payload payload = new Payload()
                .archive(resource("concord/concordOutVars"));

        ConcordProcess proc = concord.processes().start(payload);

        // ---
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---
        proc.assertLog(".*Done!.*");
    }

    /**
     * Test for concord/repositoryRefresh-task
     */
    @Test
    public void testRepositoryRefresh() throws Exception {

        Payload payload = new Payload()
                .archive(resource("concord/repositoryRefreshTask"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---
        proc.assertLog(".*Done!.*");
    }

    @Test
    public void testDryRunForChildProcess() throws Exception {
        Payload payload = new Payload()
                .parameter(Constants.Request.DRY_RUN_MODE_KEY, true)
                .archive(resource("concord/concordSubDryRun"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---
        proc.assertLog(".*Done!.*");
    }

    @Test
    public void testCreateApiKey() throws Exception {
        String apiKeyName1 = "test1_" + randomString();
        String apiKeyName2 = "test2_" + randomString();
        String apiKeyValue2 = Base64.getEncoder().encodeToString(("foo_" + randomString()).getBytes(UTF_8));

        Payload payload = new Payload()
                .archive(resource("concord/createApiKey"))
                .arg("apiKeyName1", apiKeyName1)
                .arg("apiKeyName2", apiKeyName2)
                .arg("apiKeyValue2", apiKeyValue2);

        // ---

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*result1=.*");
        proc.assertNoLog(".*result2=.*");
        proc.assertLog(".*error=.*");
        proc.assertLog(".*result3=.*");
        proc.assertLog(".*result4=.*key=" + apiKeyValue2 + ".*");

        // ---

        UUID adminId = UUID.fromString("230c5c9c-d9a7-11e6-bcfd-bb681c07b26c");

        ApiKeysApi apiKeysApi = new ApiKeysApi(concord.apiClient());
        List<ApiKeyEntry> apiKeys = apiKeysApi.listUserApiKeys(adminId);
        assertTrue(apiKeys.stream().anyMatch(k -> k.getName().equals(apiKeyName1)));
        assertTrue(apiKeys.stream().anyMatch(k -> k.getName().equals(apiKeyName2)));

        int apiKeyCount = apiKeys.size();

        // ---

        apiKeysApi = new ApiKeysApi(concord.apiClient().setApiKey(apiKeyValue2));
        apiKeys = apiKeysApi.listUserApiKeys(adminId);
        assertEquals(apiKeyCount, apiKeys.size());
    }

    @Test
    public void testCreateOrUpdateApiKey() throws Exception {
        String username = "user_" + randomString();

        UsersApi usersApi = new UsersApi(concord.apiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        // --

        String apiKeyValue = Base64.getEncoder().encodeToString(("foo_" + randomString()).getBytes(UTF_8));

        Payload payload = new Payload()
                .archive(resource("concord/createOrUpdateApiKey"))
                .arg("apiKeyValue", apiKeyValue)
                .arg("apiKeyUsername", username);

        // ---

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*result1=.*");
        proc.assertLog(".*result2=.*");
        proc.assertLog(".*result3=.*");

        // ---

        ApiKeysApi apiKeysApi = new ApiKeysApi(concord.apiClient().setApiKey(apiKeyValue));
        List<ApiKeyEntry> apiKeys = apiKeysApi.listUserApiKeys(null);
        assertEquals(1, apiKeys.size());
    }
}
