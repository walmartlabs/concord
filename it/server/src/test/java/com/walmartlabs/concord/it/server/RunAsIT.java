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
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.*;

public class RunAsIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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
        projectsApi.updateAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .setTeamId(teamId)
                .setOrgName(orgName)
                .setTeamName(teamName)
                .setLevel(ResourceAccessEntry.LevelEnum.READER));

        // Start a process

        byte[] payload = archive(RunAsIT.class.getResource("runas").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse p = start(input);
        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForStatus(processApi, p.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*username=" + userAName + ".*==.*username=" + userAName + ".*", ab);

        String formName = findForm(p.getInstanceId());

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        Map<String, Object> data = Collections.singletonMap("firstName", "xxx");

        // try submit as a wrong user

        try {
            formsApi.submit(p.getInstanceId(), formName, data);
            fail("exception expected");
        } catch (ApiException e) {
            // ignore
        }

        // submit as the proper user

        resetApiKey();

        FormSubmitResponse fsr = formsApi.submit(p.getInstanceId(), formName, data);
        assertTrue(fsr.isOk());

        pe = waitForCompletion(processApi, p.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        ab = getLog(pe.getLogFileName());
        assertLog(".*Now we are running as admin. Initiator: " + userAName + ".*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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
        projectsApi.updateAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .setTeamId(teamId)
                .setOrgName(orgName)
                .setTeamName(teamName)
                .setLevel(ResourceAccessEntry.LevelEnum.READER));

        // Start a process

        byte[] payload = archive(RunAsIT.class.getResource("runAsMultipleUsers").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("arguments.testUser", userBName);

        StartProcessResponse p = start(input);
        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForStatus(processApi, p.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*username=" + userAName + ".*==.*username=" + userAName + ".*", ab);

        String formName = findForm(p.getInstanceId());

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        Map<String, Object> data = Collections.singletonMap("firstName", "xxx");

        // try submit as a wrong user

        try {
            formsApi.submit(p.getInstanceId(), formName, data);
            fail("exception expected");
        } catch (ApiException e) {
            // ignore
        }

        // switch to the user B and submit the form
        setApiKey(apiKeyB.getKey());

        FormSubmitResponse fsr = formsApi.submit(p.getInstanceId(), formName, data);
        assertTrue(fsr.isOk());

        pe = waitForCompletion(processApi, p.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        ab = getLog(pe.getLogFileName());
        assertLog(".*Now we are running as " + userBName + ".*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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
        input.put("arguments.sudoUser", userBName);
        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*AAA: " + userAName + ".*", ab);

        // submit the form

        setApiKey(apiKeyB.getKey());

        String formName = findForm(pe.getInstanceId());

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());
        Map<String, Object> data = Collections.singletonMap("msg", "Hello!");
        formsApi.submit(pe.getInstanceId(), formName, data);

        // wait for the process to finish

        pe = waitForCompletion(processApi, spr.getInstanceId());

        // check the logs

        ab = getLog(pe.getLogFileName());
        assertLog(".*BBB: Hello!.*", ab);
        assertLog(".*CCC: " + userAName + ".*", ab);
    }

    private void createOrg(String orgName) throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        CreateOrganizationResponse r = orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));
        assertTrue(r.isOk());
    }

    private UUID createTeam(String orgName, String teamName, String... username) throws Exception {
        TeamsApi teamsApi = new TeamsApi(getApiClient());
        CreateTeamResponse ctr = teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));

        teamsApi.addUsers(orgName, teamName, false, Arrays.stream(username)
                .map(u -> new TeamUserEntry()
                        .setUsername(u)
                        .setRole(TeamUserEntry.RoleEnum.MEMBER))
                .collect(Collectors.toList()));

        return ctr.getId();
    }

    private CreateApiKeyResponse addUser(String userAName) throws ApiException {
        UsersApi usersApi = new UsersApi(getApiClient());

        usersApi.createOrUpdate(new CreateUserRequest().setUsername(userAName).setType(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        return apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));
    }

    private void createProject(String orgName, String projectName) throws ApiException {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse por = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setVisibility(ProjectEntry.VisibilityEnum.PRIVATE)
                .setAcceptsRawPayload(true));
        assertTrue(por.isOk());
    }

    private String findForm(UUID instanceId) throws Exception {
        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(instanceId);
        assertEquals(1, forms.size());

        // ---

        FormListEntry f0 = forms.get(0);
        assertFalse(f0.isCustom());

        return f0.getName();
    }
}
