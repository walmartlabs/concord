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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.Test;

import javax.xml.bind.DatatypeConverter;
import java.io.InputStream;
import java.util.*;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.*;

public class ConcordTaskIT extends AbstractServerIT {

    @Test
    public void testStartArchive() throws Exception {
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
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));

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
                .orgName(orgName)
                .teamName(teamName)
                .level(ResourceAccessEntry.LevelEnum.READER));

        // start a new process using the project as the user A

        byte[] payload = archive(ConcordTaskIT.class.getResource("concordTask").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FINISHED);

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Done!.*", ab);
    }

    @Test
    public void testStartDirectory() throws Exception {
        byte[] payload = archive(ConcordTaskIT.class.getResource("concordDirTask").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Done! Hello! Good Bye.*", ab);
    }

    @Test
    public void testCreateProject() throws Exception {
        String projectName = "project_" + randomString();

        byte[] payload = archive(ConcordTaskIT.class.getResource("concordProjectTask").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.newProjectName", projectName);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Done!.*", ab);
    }

    @Test
    public void testStartAt() throws Exception {
        byte[] payload = archive(ConcordTaskIT.class.getResource("concordDirTask").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        Calendar c = Calendar.getInstance();
        c.add(Calendar.SECOND, 30);
        input.put("arguments.startAt", DatatypeConverter.printDateTime(c));

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Done! Hello!.*", ab);
    }

    @Test
    public void testOutVarsNotFound() throws Exception {
        byte[] payload = archive(ConcordTaskIT.class.getResource("concordOutVars").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Done!.*", ab);
    }

    @Test
    public void testSubprocessFail() throws Exception {
        byte[] payload = archive(ConcordTaskIT.class.getResource("concordSubFail").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Fail!.*", ab);
        assertNoLog(".*Done!.*", ab);
    }

    @Test
    public void testSubprocessIgnoreFail() throws Exception {
        byte[] payload = archive(ConcordTaskIT.class.getResource("concordSubIgnoreFail").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Done!.*", ab);
    }

    @Test
    public void testStartChildFinishedWithError() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        byte[] payload = archive(ConcordTaskIT.class.getResource("concordTaskFailChild").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FAILED);

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Child process.*FAILED.*BOOOM.*", ab);
    }

    @Test
    public void testForkWithItemsWithOutVariable() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        byte[] payload = archive(ConcordTaskIT.class.getResource("concordTaskForkWithItemsWithOut").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FAILED);

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*color=RED.*", ab);
        assertLog(".*color=WHITE.*", ab);
        assertLog(".*Done.*\\[\\[.*\\], \\[.*\\]\\] is completed.*", ab);
    }

    @Test
    public void testForkWithItems() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        byte[] payload = archive(ConcordTaskIT.class.getResource("concordTaskForkWithItems").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FAILED);

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*color=RED.*", ab);
        assertLog(".*color=WHITE.*", ab);
        assertLog(".*Done.*\\[\\[.*\\], \\[.*\\]\\] is completed.*", ab);
    }

    @Test
    public void testExternalApiToken() throws Exception {
        String username = "user_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeysApi.createUserApiKey(new CreateApiKeyRequest()
                .username(username));

        // ---

        byte[] payload = archive(ConcordTaskIT.class.getResource("concordTaskApiKey").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.myApiKey", cakr.getKey());

        StartProcessResponse spr = start(input);

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(pe.getInstanceId());
        assertLog(".*Hello, Concord!.*", ab);
    }

    @Test
    public void testSuspendParentProcess() throws Exception {
        String username = "user_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeysApi.createUserApiKey(new CreateApiKeyRequest()
                .username(username));

        // ---

        byte[] payload = archive(ConcordTaskIT.class.getResource("concordTaskSuspendParentProcess").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.myApiKey", cakr.getKey());

        StartProcessResponse spr = start(input);

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(pe.getInstanceId());
        assertLog(".*Hello, Concord!.*", ab);
    }

    @Test
    public void testSubWithNullArgValue() throws Exception {
        byte[] payload = archive(ConcordTaskIT.class.getResource("concordSubWithNullArg").toURI());

        StartProcessResponse parentSpr = start(payload);

        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        waitForCompletion(getApiClient(), parentSpr.getInstanceId());

        ProcessEntry processEntry = processApi.getProcess(parentSpr.getInstanceId(), Collections.singleton("childrenIds"));
        assertEquals(1, processEntry.getChildrenIds().size());

        ProcessEntry child = processApi.getProcess(processEntry.getChildrenIds().iterator().next(), Collections.singleton("childrenIds"));
        assertNotNull(child);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, child.getStatus());

        // ---

        byte[] ab = getLog(child.getInstanceId());
        assertLog(".*Child process, nullValue: ''.*", ab);
        assertLog(".*Child process, nullValue == null: 'true'.*", ab);
        assertLog(".*Child process, hasVariable\\('nullValue'\\): true.*", ab);
    }

    @Test
    public void testForkWithArguments() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        byte[] payload = archive(ConcordTaskIT.class.getResource("concordTaskForkWithArguments").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse parentSpr = start(input);

        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        waitForCompletion(getApiClient(), parentSpr.getInstanceId());

        ProcessEntry processEntry = processApi.getProcess(parentSpr.getInstanceId(), Collections.singleton("childrenIds"));
        assertEquals(1, processEntry.getChildrenIds().size());

        ProcessEntry child = processApi.getProcess(processEntry.getChildrenIds().iterator().next(), Collections.singleton("childrenIds"));
        assertNotNull(child);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, child.getStatus());

        // ---

        byte[] ab = getLog(child.getInstanceId());
        assertLog(".*Hello from a subprocess.*", ab);
        assertLog(".*Concord Fork Process 123.*", ab);
    }

    @Test
    public void testForkWithForm() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        byte[] payload = archive(ConcordTaskIT.class.getResource("concordTaskForkWithForm").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse parentSpr = start(input);

        // ---

        ProcessEntry pir = waitForStatus(getApiClient(), parentSpr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.listProcessForms(parentSpr.getInstanceId());
        assertEquals(1, forms.size());

        Map<String, Object> data = new HashMap<>();
        data.put("firstName", "Boo");
        FormSubmitResponse fsr = formsApi.submitForm(pir.getInstanceId(), "myForm", data);
        assertTrue(fsr.getOk());

        waitForCompletion(getApiClient(), parentSpr.getInstanceId());

        // ---
        ProcessV2Api processApi = new ProcessV2Api(getApiClient());

        ProcessEntry processEntry = processApi.getProcess(parentSpr.getInstanceId(), Collections.singleton("childrenIds"));
        assertEquals(1, processEntry.getChildrenIds().size());

        ProcessEntry child = processApi.getProcess(processEntry.getChildrenIds().iterator().next(), Collections.singleton("childrenIds"));
        assertNotNull(child);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, child.getStatus());

        // ---

        byte[] ab = getLog(child.getInstanceId());
        assertLog(".*Hello from a subprocess.*", ab);
        assertLog(".*Concord Fork Process 234.*", ab);
    }

    @Test
    public void testForkSuspend() throws Exception {
        String nameVar = "name_" + randomString();

        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        byte[] payload = archive(ConcordTaskIT.class.getResource("concordTaskForkSuspend").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("arguments.name", nameVar);

        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(pe.getInstanceId());
        assertLog(".*\\{varFromFork=Hello, " + nameVar + "\\}.*", ab);
        assertLog(".*\\{varFromFork=Bye, " + nameVar + "\\}.*", ab);
    }

    @Test
    public void testForkAsyncGrabOutVars() throws Exception {
        byte[] payload = archive(ConcordTaskIT.class.getResource("concordTaskForkAsyncGrabOutVars").toURI());

        StartProcessResponse spr = start(payload);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---
        byte[] ab = getLog(pe.getInstanceId());
        if (grep(".*\\{x=1, y=2, z=3\\}.*", ab).isEmpty()
                || grep(".*\\{a=4, b=5, c=6\\}.*", ab).isEmpty()) {

            for (UUID id : pe.getChildrenIds()) {
                ProcessEntry pp = new ProcessV2Api(getApiClient()).getProcess(id, Collections.singleton("childrenIds"));
                System.out.println("process: " + pp.getInstanceId() + ", status: " + pp.getStatus() + ", out: " + getOutVars(id, processApi));
                System.out.println(">>>");
            }
        }
        assertLog(".*\\{x=1, y=2, z=3\\}.*", ab);
        assertLog(".*\\{a=4, b=5, c=6\\}.*", ab);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getOutVars(UUID id, ProcessApi processApi) throws Exception {
        try (InputStream is = processApi.downloadAttachment(id, "out.json")) {
            ObjectMapper om = new ObjectMapper();
            return om.readValue(is, Map.class);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    @Test
    public void testParentInstanceId() throws Exception {
        byte[] payload = archive(ConcordTaskIT.class.getResource("parentInstanceId").toURI());

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        ProcessV2Api processV2Api = new ProcessV2Api(getApiClient());
        ProcessListFilter filter = ProcessListFilter.builder()
                .parentInstanceId(pe.getInstanceId())
                .build();

        List<ProcessEntry> l = processV2Api.listProcesses(filter);
        assertEquals(2, l.size());

        for (ProcessEntry e : l) {
            byte[] ab = getLog(e.getInstanceId());
            assertLog(".*parentInstanceId: " + pe.getInstanceId() + ".*", ab);
        }
    }
}
