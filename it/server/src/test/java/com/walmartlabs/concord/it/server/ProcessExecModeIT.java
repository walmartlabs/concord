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

import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.client2.CreateUserRequest.TypeEnum.LOCAL;
import static com.walmartlabs.concord.client2.ProjectEntry.ProcessExecModeEnum.*;
import static com.walmartlabs.concord.client2.ProjectEntry.RawPayloadModeEnum.EVERYONE;
import static com.walmartlabs.concord.client2.ProjectEntry.VisibilityEnum.PRIVATE;
import static com.walmartlabs.concord.client2.ProjectEntry.VisibilityEnum.PUBLIC;
import static com.walmartlabs.concord.client2.ResourceAccessEntry.LevelEnum.READER;
import static com.walmartlabs.concord.client2.ResourceAccessEntry.LevelEnum.WRITER;
import static com.walmartlabs.concord.client2.TeamUserEntry.RoleEnum.MEMBER;
import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ProcessExecModeIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        var projectsApi = new ProjectsApi(getApiClient());
        var usersApi = new UsersApi(getApiClient());
        var apiKeyResource = new ApiKeysApi(getApiClient());
        var teamsApi = new TeamsApi(getApiClient());

        // ---

        var orgName = "Default";

        // create userA

        var userAName = "userA_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest().username(userAName).type(LOCAL));
        var apiKeyA = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userAName));

        // create userB

        var userBName = "userB_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest().username(userBName).type(LOCAL));
        var apiKeyB = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userBName));

        // create a new team and add userB

        var teamName = "team_" + randomString();
        var team = teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));
        teamsApi.addUsersToTeam(orgName, teamName, false, List.of(new TeamUserEntry().username(userBName).role(MEMBER)));

        // create the project and assign the team to it

        var projectName = "project_" + randomString();
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(PUBLIC)
                .rawPayloadMode(EVERYONE));

        projectsApi.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .teamId(team.getId())
                .orgName(orgName)
                .teamName(teamName)
                .level(READER));

        // check the project defaults

        var projectEntry = projectsApi.getProject(orgName, projectName);
        assertEquals(READERS, projectEntry.getProcessExecMode());

        // switch to userA, no extra permissions or teams assigned
        // the project is public, the user should be able to execute a process within the context of the project

        setApiKey(apiKeyA.getKey());
        execProcess(orgName, projectName);

        // switch to userB
        // the project is still public, the user B should be able to execute a process too

        setApiKey(apiKeyB.getKey());
        execProcess(orgName, projectName);

        // switch back to admin and make the project private

        resetApiKey();
        projectsApi.createOrUpdateProject(orgName, projectEntry.visibility(PRIVATE));

        // switch to userA
        // the project is private now, userA should NOT be able to execute any process within the project

        setApiKey(apiKeyA.getKey());
        try {
            execProcess(orgName, projectName);
            fail("exec attempt must be rejected");
        } catch (ApiException e) {
        }

        // switch to userB
        // the project is still private, but userB is in the team that has READER privileges and thus should be able to execute a process

        setApiKey(apiKeyB.getKey());
        execProcess(orgName, projectName);

        // switch back to admin and update the project and set the exec mode to WRITERs only

        resetApiKey();
        projectsApi.createOrUpdateProject(orgName, projectEntry.visibility(PRIVATE).processExecMode(WRITERS));

        // switch to userB
        // the project now requires WRITER privileges to execute processes, the attempt must fail

        setApiKey(apiKeyB.getKey());
        try {
            execProcess(orgName, projectName);
            fail("exec attempt must be rejected");
        } catch (ApiException e) {
        }

        // switch back to admin and update the team's privileges

        resetApiKey();
        projectsApi.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .teamId(team.getId())
                .orgName(orgName)
                .teamName(teamName)
                .level(WRITER));

        // switch to userB
        // the userB's team now has the necessary privileges and should be able to execute a process

        setApiKey(apiKeyB.getKey());
        execProcess(orgName, projectName);

        // switch back to admin and disable process execution altogether

        projectsApi.createOrUpdateProject(orgName, projectEntry.visibility(PRIVATE).processExecMode(DISABLED));

        // switch to userB and try starting a process

        setApiKey(apiKeyB.getKey());
        try {
            execProcess(orgName, projectName);
            fail("exec attempt must be rejected");
        } catch (ApiException e) {
        }
    }

    private void execProcess(String orgName, String projectName) throws Exception {
        byte[] payload = archive(ProcessExecModeIT.class.getResource("processModeExec").toURI());
        start(Map.of("org", orgName,
                "project", projectName,
                "archive", payload));
    }
}
