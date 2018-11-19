package com.walmartlabs.concord.it.amazons3;

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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.it.common.ServerClient;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class S3IT {
    private ServerClient serverClient;

    @Before
    public void init() {
        serverClient = new ServerClient(ITConstants.SERVER_URL);
    }

    @Test(timeout = 60000)
    public void processStateArchiveTest() throws Exception {
        ApiClient apiClient = serverClient.getClient();
        // create a new org

        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(serverClient.getClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // add the user A

        UsersApi usersApi = new UsersApi(serverClient.getClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest().setUsername(userAName).setType(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(apiClient);
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));

        // create the user A's team

        String teamName = "team_" + randomString();

        TeamsApi teamsApi = new TeamsApi(serverClient.getClient());
        CreateTeamResponse ctr = teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));

        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userAName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));

        // switch to the user A and create a new private project

        serverClient.setApiKey(apiKeyA.getKey());

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(serverClient.getClient());
        ProjectOperationResponse por = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setVisibility(ProjectEntry.VisibilityEnum.PRIVATE)
                .setAcceptsRawPayload(true));

        // grant the team access to the project

        projectsApi.updateAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .setTeamId(ctr.getId())
                .setOrgName(orgName)
                .setTeamName(teamName)
                .setLevel(ResourceAccessEntry.LevelEnum.READER));

        // start a process with zero child

        byte[] payload = archive(S3IT.class.getResource("stateArchive").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse singleNodeProcess = serverClient.start(input);
        ProcessApi processApi = new ProcessApi(serverClient.getClient());
        ProcessEntry pir = waitForCompletion(processApi, singleNodeProcess.getInstanceId());

        assertEquals("FINISHED", pir.getStatus().toString());

        boolean isArchivedDone = false;
        while (!isArchivedDone) {
            isArchivedDone = processApi.isStateArchived(pir.getInstanceId());
            Thread.sleep(500);
        }
        assertTrue(isArchivedDone);
    }

    @Test(timeout = 60000)
    public void processCheckPointArchiveTest() throws Exception {

        ApiClient apiClient = serverClient.getClient();
        // create a new org

        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(serverClient.getClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // add the user A

        UsersApi usersApi = new UsersApi(serverClient.getClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest().setUsername(userAName).setType(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(apiClient);
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));

        // create the user A's team

        String teamName = "team_" + randomString();

        TeamsApi teamsApi = new TeamsApi(serverClient.getClient());
        CreateTeamResponse ctr = teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));

        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userAName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));

        // switch to the user A and create a new private project

        serverClient.setApiKey(apiKeyA.getKey());

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(serverClient.getClient());
        ProjectOperationResponse por = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setVisibility(ProjectEntry.VisibilityEnum.PRIVATE)
                .setAcceptsRawPayload(true));

        // grant the team access to the project

        projectsApi.updateAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .setTeamId(ctr.getId())
                .setOrgName(orgName)
                .setTeamName(teamName)
                .setLevel(ResourceAccessEntry.LevelEnum.READER));

        // start a process with zero child

        byte[] payload = archive(S3IT.class.getResource("checkpointArchive").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse singleNodeProcess = serverClient.start(input);
        ProcessApi processApi = new ProcessApi(serverClient.getClient());
        ProcessEntry pir = waitForCompletion(processApi, singleNodeProcess.getInstanceId());

        assertEquals("FINISHED", pir.getStatus().toString());

        CheckpointApi checkpointApi = new CheckpointApi(serverClient.getClient());
        List<ProcessCheckpointEntry> list = checkpointApi.list(pir.getInstanceId());

        assertEquals(2, list.size());
        while (list.size() > 0) {
            list = checkpointApi.list(pir.getInstanceId());
            Thread.sleep(500);
        }

        assertEquals(0, list.size());
    }
}
