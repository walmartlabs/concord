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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;
import static org.junit.jupiter.api.Assertions.fail;

public class ProcessRbacIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        // create a new org

        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // add the user A

        UsersApi usersApi = new UsersApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest().username(userAName).type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKeyA = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userAName));

        // create the user A's team

        String teamName = "team_" + randomString();

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        CreateTeamResponse ctr = teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));

        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(userAName)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        // switch to the user A and create a new private project

        setApiKey(apiKeyA.getKey());

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PRIVATE)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // grant the team access to the project

        projectsApi.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .teamId(ctr.getId())
                .orgName(orgName)
                .teamName(teamName)
                .level(ResourceAccessEntry.LevelEnum.READER));

        // start a new process using the project as the user A

        byte[] payload = archive(ProcessRbacIT.class.getResource("processRbac").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse spr = start(input);

        waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FINISHED);

        // switch to admin and add the user B

        resetApiKey();

        String userBName = "userB_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userBName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        CreateApiKeyResponse apiKeyB = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userBName));

        // switch to the user B and try starting a process using the project

        setApiKey(apiKeyB.getKey());

        try {
            start(input);
            fail("Should fail");
        } catch (ApiException e) {
        }

        // switch to admin and add the user B to the team

        resetApiKey();

        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(userBName)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        // switch to the user B and start a process using the project

        setApiKey(apiKeyB.getKey());

        spr = start(input);
        waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FINISHED);
    }
}
