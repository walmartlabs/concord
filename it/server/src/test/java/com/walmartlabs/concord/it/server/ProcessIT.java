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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.client.ProcessEntry.StatusEnum;
import com.walmartlabs.concord.common.IOUtils;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.*;

public class ProcessIT extends AbstractServerIT {

    @Test
    @Ignore
    public void testLotsOfProcesses() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        int count = 100;
        for (int i = 0; i < count; i++) {
            start(payload);
        }
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testTimeout() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("timeout").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        try {
            processApi.waitForCompletion(spr.getInstanceId(), 3000L);
            fail("should fail");
        } catch (ApiException e) {
            String s = e.getResponseBody();
            ProcessEntry pir = getApiClient().getJSON().deserialize(s, ProcessEntry.class);
            assertTrue("Unexpected status: " + pir.getStatus(), StatusEnum.RUNNING.equals(pir.getStatus())
                    || StatusEnum.ENQUEUED.equals(pir.getStatus())
                    || StatusEnum.PREPARING.equals(pir.getStatus())
                    || StatusEnum.STARTING.equals(pir.getStatus()));
        }

        processApi.kill(spr.getInstanceId());

        waitForStatus(processApi, spr.getInstanceId(), StatusEnum.CANCELLED, StatusEnum.FAILED, StatusEnum.FINISHED);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testTaskOut() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("taskOut").toURI(), ITConstants.DEPENDENCIES_DIR);

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*I said: Hello!.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testDelegateOut() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("delegateOut").toURI(), ITConstants.DEPENDENCIES_DIR);

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*I said: Hello!.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testProjectId() throws Exception {
        String orgName = "Default";
        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse por1 = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);
        StartProcessResponse sprA = start(input);
        waitForCompletion(processApi, sprA.getInstanceId());

        // ---

        StartProcessResponse sprB = start(payload);
        waitForCompletion(processApi, sprB.getInstanceId());

        // ---

        String anotherProjectName = "another_" + randomString();
        ProjectOperationResponse por2 = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(anotherProjectName));

        List<ProcessEntry> l = processApi.list(null, null, por2.getId(), null, null, null, null, null, null, 30, 0);
        assertTrue(l.isEmpty());

        l = processApi.list(null, null, por1.getId(), null, null, null, null, null, null, 30, 0);
        assertEquals(1, l.size());

        l = processApi.list(null, null, null, null, null, null, null, null, null, 30, 0);
        ProcessEntry p = null;
        for (ProcessEntry e : l) {
            if (e.getInstanceId().equals(sprB.getInstanceId())) {
                p = e;
                break;
            }
        }
        assertNotNull(p);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testGetAllProcessesForChildIds() throws Exception {
        // create a new org

        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // add the user A

        UsersApi usersApi = new UsersApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest().setUsername(userAName).setType(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));

        // create the user A's team

        String teamName = "team_" + randomString();

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        CreateTeamResponse ctr = teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));

        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userAName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));

        // switch to the user A and create a new private project

        setApiKey(apiKeyA.getKey());

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse por = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setVisibility(ProjectEntry.VisibilityEnum.PRIVATE)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // grant the team access to the project

        projectsApi.updateAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .setTeamId(ctr.getId())
                .setOrgName(orgName)
                .setTeamName(teamName)
                .setLevel(ResourceAccessEntry.LevelEnum.READER));

        //Start a process with zero child

        byte[] payload = archive(ProcessIT.class.getResource("process").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse singleNodeProcess = start(input);
        ProcessApi processApi = new ProcessApi(getApiClient());
        waitForCompletion(processApi, singleNodeProcess.getInstanceId());

        // Start a process with children

        payload = archive(ProcessIT.class.getResource("processWithChildren").toURI());
        input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse parentSpr = start(input);
        waitForCompletion(processApi, parentSpr.getInstanceId());

        // ---

        List<ProcessEntry> processEntry = processApi.list(null, null, por.getId(), null, null, null, null, null, null, 10, 0);
        for (ProcessEntry pe : processEntry) {
            if (pe.getInstanceId().equals(singleNodeProcess.getInstanceId())) {
                assertTrue(pe.getChildrenIds() == null || pe.getChildrenIds().isEmpty());
            } else if (pe.getInstanceId().equals(parentSpr.getInstanceId())) {
                assertEquals(3, pe.getChildrenIds() != null ? pe.getChildrenIds().size() : 0);
            }
        }
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testForkInitiatorFromApiKey() throws Exception {
        // add the user A
        UsersApi usersApi = new UsersApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest().setUsername(userAName).setType(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));

        String yaml = new String(Files.readAllBytes(Paths.get(ProcessIT.class.getResource("forkInitiator").toURI()).resolve("concord.yml")));
        yaml = yaml.replace("{{apiKey}}", apiKeyA.getKey());

        Path tmp = Files.createTempDirectory("concord-it-fork");
        Files.write(tmp.resolve("concord.yml"), yaml.getBytes());

        byte[] payload = archive(tmp.toUri());
        IOUtils.deleteRecursively(tmp);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse parentSpr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());

        ProcessEntry processEntry = waitForCompletion(processApi, parentSpr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, processEntry.getStatus());
        assertEquals(1, processEntry.getChildrenIds().size());

        ProcessEntry child = waitForCompletion(processApi, processEntry.getChildrenIds().get(0));
        assertEquals(ProcessEntry.StatusEnum.FINISHED, child.getStatus());

        {
            byte[] ab = getLog(processEntry.getLogFileName());
            assertLog(".*initiator: .*admin.*", ab);
        }
        {
            byte[] ab = getLog(child.getLogFileName());
            assertLog(".*initiator: .*" + userAName + ".*", ab);
        }
    }
}
