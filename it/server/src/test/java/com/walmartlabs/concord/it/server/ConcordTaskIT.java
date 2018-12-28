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

import com.walmartlabs.concord.client.*;
import org.junit.Test;

import javax.xml.bind.DatatypeConverter;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.assertEquals;

public class ConcordTaskIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testStartArchive() throws Exception {
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
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setVisibility(ProjectEntry.VisibilityEnum.PRIVATE)
                .setAcceptsRawPayload(true));

        // grant the team access to the project

        projectsApi.updateAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .setOrgName(orgName)
                .setTeamName(teamName)
                .setLevel(ResourceAccessEntry.LevelEnum.READER));

        // start a new process using the project as the user A

        byte[] payload = archive(ProcessRbacIT.class.getResource("concordTask").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.FINISHED);

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Done!.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testStartDirectory() throws Exception {
        byte[] payload = archive(ProcessRbacIT.class.getResource("concordDirTask").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Done! Hello!.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testCreateProject() throws Exception {
        String projectName = "project_" + randomString();

        byte[] payload = archive(ProcessRbacIT.class.getResource("concordProjectTask").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.newProjectName", projectName);

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Done!.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testStartAt() throws Exception {
        byte[] payload = archive(ProcessRbacIT.class.getResource("concordDirTask").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        Calendar c = Calendar.getInstance();
        c.add(Calendar.SECOND, 30);
        input.put("arguments.startAt", DatatypeConverter.printDateTime(c));

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Done! Hello!.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testOutVarsNotFound() throws Exception {
        byte[] payload = archive(ProcessRbacIT.class.getResource("concordOutVars").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Done!.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testSubprocessFail() throws Exception {
        byte[] payload = archive(ProcessRbacIT.class.getResource("concordSubFail").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Fail!.*", ab);
        assertNoLog(".*Done!.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testSubprocessIgnoreFail() throws Exception {
        byte[] payload = archive(ProcessRbacIT.class.getResource("concordSubIgnoreFail").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Done!.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testStartChildFinishedWithError() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setVisibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .setAcceptsRawPayload(true));

        byte[] payload = archive(ProcessRbacIT.class.getResource("concordTaskFailChild").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.FAILED);

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Child process.*FAILED.*BOOOM.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testForkWithItemsWithOutVariable() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setVisibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .setAcceptsRawPayload(true));

        byte[] payload = archive(ProcessRbacIT.class.getResource("concordTaskForkWithItemsWithOut").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.FAILED);

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*color=RED.*", ab);
        assertLog(".*color=WHITE.*", ab);
        assertLog(".*Done.*\\[\\[.*\\], \\[.*\\]\\] is completed.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testForkWithItems() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setVisibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .setAcceptsRawPayload(true));

        byte[] payload = archive(ProcessRbacIT.class.getResource("concordTaskForkWithItems").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.FAILED);

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*color=RED.*", ab);
        assertLog(".*color=WHITE.*", ab);
        assertLog(".*Done.*\\[\\[.*\\], \\[.*\\]\\] is completed.*", ab);
    }
}
