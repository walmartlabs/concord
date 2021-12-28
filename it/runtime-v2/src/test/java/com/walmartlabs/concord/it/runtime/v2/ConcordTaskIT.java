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
import com.walmartlabs.concord.client.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static com.walmartlabs.concord.it.runtime.v2.ITConstants.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public class ConcordTaskIT {

    @RegisterExtension
    public static final ConcordRule concord = ConcordConfiguration.configure();

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
        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

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
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(username)
                .setType(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeysApi = new ApiKeysApi(concord.apiClient());
        CreateApiKeyResponse cakr = apiKeysApi.create(new CreateApiKeyRequest()
                .setUsername(username));

        // ---
        Payload payload = new Payload()
                .archive(resource("concord/concordTaskApiKey"))
                .arg("myApiKey", cakr.getKey());

        ConcordProcess proc = concord.processes().start(payload);

        // ---
        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

        // ---
        proc.assertLog(".*Hello, Concord!. From: .*" + username + ".*");
    }

    @Test
    public void testSuspendParentProcess() throws Exception {
        Payload payload = new Payload()
                .archive(ConcordTaskIT.class.getResource("concord/concordTaskSuspendParentProcess").toURI());

        ConcordProcess proc = concord.processes().start(payload);
        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

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
        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);
        ProcessEntry processEntry = proc.getEntry("childrenIds");

        // ---
        assertEquals(1, processEntry.getChildrenIds().size());

        ConcordProcess child = concord.processes().get(processEntry.getChildrenIds().get(0));

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
        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

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
        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

        // ---
        proc.assertLog(".*Done!.*");
    }

    @Test
    public void testOutVarsNotFound() throws Exception {
        Payload payload = new Payload()
                .archive(resource("concord/concordOutVars"));

        ConcordProcess proc = concord.processes().start(payload);

        // ---
        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

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
        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

        // ---
        proc.assertLog(".*Done!.*");
    }

    private static URI resource(String name) throws URISyntaxException {
        URL url = ConcordTaskIT.class.getResource(name);
        assertNotNull(url, "can't find '" + name + "'");
        return url.toURI();
    }
}
