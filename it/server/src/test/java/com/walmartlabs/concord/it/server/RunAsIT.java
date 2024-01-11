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

import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.*;

public class RunAsIT extends AbstractServerIT {

    @Test
    public void testSwitchCurrentUser() throws Exception {
        // create a new org

        String orgName = "org_" + randomString();
        createOrg(orgName);

        // add the user A

        String userAName = "userA_" + randomString();
        CreateApiKeyResponse apiKeyA = addUser(userAName);

        // create the user A's team

        String teamName = "team_" + randomString();
        UUID teamId = createTeam(orgName, teamName, userAName);

        // switch to the user A and create a new project

        setApiKey(apiKeyA.getKey());

        String projectName = "project_" + randomString();
        createProject(orgName, projectName);

        // grant the team access to the project

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .teamId(teamId)
                .orgName(orgName)
                .teamName(teamName)
                .level(ResourceAccessEntry.LevelEnum.READER));

        // Start a process

        byte[] payload = archive(RunAsIT.class.getResource("runas").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse p = start(input);
        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForStatus(getApiClient(), p.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        byte[] ab = getLog(pe.getInstanceId());
        // assume Concord forces all user/domain names to lower case
        assertLog(".*username=" + userAName.toLowerCase() + ".*==.*username=" + userAName.toLowerCase() + ".*", ab);

        String formName = findForm(p.getInstanceId());

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        Map<String, Object> data = Collections.singletonMap("firstName", "xxx");

        // try submit as a wrong user

        try {
            formsApi.submitForm(p.getInstanceId(), formName, data);
            fail("exception expected");
        } catch (ApiException e) {
            // ignore
        }

        // submit as the proper user

        resetApiKey();

        FormSubmitResponse fsr = formsApi.submitForm(p.getInstanceId(), formName, data);
        assertTrue(fsr.getOk());

        pe = waitForCompletion(getApiClient(), p.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        ab = getLog(pe.getInstanceId());
        assertLog(".*Now we are running as admin. Initiator: " + userAName.toLowerCase() + ".*", ab);
    }

    @Test
    public void testWithMultipleUsers() throws Exception {
        // create a new org

        String orgName = "org_" + randomString();
        createOrg(orgName);

        // add the user A

        String userAName = "userA_" + randomString();
        CreateApiKeyResponse apiKeyA = addUser(userAName);

        // add the user B

        String userBName = "userB_" + randomString();
        CreateApiKeyResponse apiKeyB = addUser(userBName);

        // create the user's team

        String teamName = "team_" + randomString();
        UUID teamId = createTeam(orgName, teamName, userAName, userBName);

        // switch to the user A and create a new project

        setApiKey(apiKeyA.getKey());

        String projectName = "project_" + randomString();
        createProject(orgName, projectName);

        // grant the team access to the project

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .teamId(teamId)
                .orgName(orgName)
                .teamName(teamName)
                .level(ResourceAccessEntry.LevelEnum.READER));

        // Start a process

        byte[] payload = archive(RunAsIT.class.getResource("runAsMultipleUsers").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("arguments.testUser", userBName.toLowerCase());

        StartProcessResponse p = start(input);
        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForStatus(getApiClient(), p.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        byte[] ab = getLog(pe.getInstanceId());
        // assume Concord forces all user/domain names to lower case
        assertLog(".*username=" + userAName.toLowerCase() + ".*==.*username=" + userAName.toLowerCase() + ".*", ab);

        String formName = findForm(p.getInstanceId());

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        Map<String, Object> data = Collections.singletonMap("firstName", "xxx");

        // try submit as a wrong user

        try {
            formsApi.submitForm(p.getInstanceId(), formName, data);
            fail("exception expected");
        } catch (ApiException e) {
            // ignore
        }

        // switch to the user B and submit the form
        setApiKey(apiKeyB.getKey());

        FormSubmitResponse fsr = formsApi.submitForm(p.getInstanceId(), formName, data);
        assertTrue(fsr.getOk());

        pe = waitForCompletion(getApiClient(), p.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // starting from 1.39.0 the log endpoint performs additional RBAC checks
        // in this case user B doesn't have permissions to access the log
        try {
            getLog(pe.getInstanceId());
            fail("should fail");
        } catch (ApiException e) {
            assertEquals(403, e.getCode());
        }

        // switch to the user A and fetch the log again

        setApiKey(apiKeyA.getKey());
        ab = getLog(pe.getInstanceId());

        assertLog(".*Now we are running as " + userBName.toLowerCase() + ".*", ab);
    }

    @Test
    public void testPayload() throws Exception {
        // create a new org

        String orgName = "org_" + randomString();
        createOrg(orgName);

        // add users

        String userAName = "userA_" + randomString();
        String userBName = "userB_" + randomString();

        CreateApiKeyResponse apiKeyA = addUser(userAName);
        CreateApiKeyResponse apiKeyB = addUser(userBName);

        // start a new process

        setApiKey(apiKeyA.getKey());

        byte[] payload = archive(RunAsIT.class.getResource("runAsPayload").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.sudoUser", userBName.toLowerCase());
        StartProcessResponse spr = start(input);

        ProcessEntry pe = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        byte[] ab = getLog(pe.getInstanceId());
        // assume Concord forces all user/domain names to lower case
        assertLog(".*AAA: " + userAName.toLowerCase() + ".*", ab);

        // submit the form

        setApiKey(apiKeyB.getKey());

        String formName = findForm(pe.getInstanceId());

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());
        Map<String, Object> data = Collections.singletonMap("msg", "Hello!");
        formsApi.submitForm(pe.getInstanceId(), formName, data);

        // wait for the process to finish

        pe = waitForCompletion(getApiClient(), spr.getInstanceId());

        // check the logs

        resetApiKey();

        ab = getLog(pe.getInstanceId());
        assertLog(".*BBB: Hello!.*", ab);
        // assume Concord forces all user/domain names to lower case
        assertLog(".*CCC: " + userAName.toLowerCase() + ".*", ab);
    }

    private void createOrg(String orgName) throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        CreateOrganizationResponse r = orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        assertTrue(r.getOk());
    }

    private UUID createTeam(String orgName, String teamName, String... username) throws Exception {
        TeamsApi teamsApi = new TeamsApi(getApiClient());
        CreateTeamResponse ctr = teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));

        teamsApi.addUsersToTeam(orgName, teamName, false, Arrays.stream(username)
                .map(u -> new TeamUserEntry()
                        .username(u)
                        .role(TeamUserEntry.RoleEnum.MEMBER))
                .collect(Collectors.toList()));

        return ctr.getId();
    }

    private CreateApiKeyResponse addUser(String userAName) throws ApiException {
        UsersApi usersApi = new UsersApi(getApiClient());

        usersApi.createOrUpdateUser(new CreateUserRequest().username(userAName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        return apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userAName));
    }

    private void createProject(String orgName, String projectName) throws ApiException {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse por = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PRIVATE)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
        assertTrue(por.getOk());
    }

    private String findForm(UUID instanceId) throws Exception {
        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.listProcessForms(instanceId);
        assertEquals(1, forms.size());

        // ---

        FormListEntry f0 = forms.get(0);
        assertFalse(f0.getCustom());

        return f0.getName();
    }
}
