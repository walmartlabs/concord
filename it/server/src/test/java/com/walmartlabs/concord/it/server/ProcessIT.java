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
import com.walmartlabs.concord.client.ProcessEntry.KindEnum;
import com.walmartlabs.concord.client.ProcessEntry.StatusEnum;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.*;

public class ProcessIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testUploadAndRun() throws Exception {
        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, world.*", ab);
        assertLog(".*Hello, local files!.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testDefaultEntryPoint() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("defaultEntryPoint").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, Concord!.*", ab);
    }

    @Test
    @Ignore
    public void testLotsOfProcesses() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        int count = 100;
        for (int i = 0; i < count; i++) {
            ProcessApi processApi = new ProcessApi(getApiClient());
            start(payload);
        }
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testUploadAndRunSync() throws Exception {
        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("process-sync").toURI());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("sync", true);
        StartProcessResponse spr = start(input);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*110123.*", ab);
        assertLog(".*Boo Zoo.*", ab);
        assertLog(".*100022.*", ab);
        assertLog(".*120123.*", ab);
        assertLog(".*redColor.*", ab);

        assertTrue(pir.getStatus() == StatusEnum.FINISHED);
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
    public void testInterpolation() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("interpolation").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, world.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testErrorHandling() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("errorHandling").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Kaboom.*", ab);
        assertLog(".*We got:.*java.lang.RuntimeException.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testStartupProblem() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("startupProblem").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForStatus(processApi, spr.getInstanceId(), StatusEnum.FAILED);

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*gaaarbage.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testMultipart() throws Exception {
        String zVal = "z" + randomString();
        String myFileVal = "myFile" + randomString();
        byte[] payload = archive(ProcessIT.class.getResource("multipart").toURI());


        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("entryPoint", "main");
        input.put("arguments.z", zVal);
        input.put("myfile.txt", myFileVal.getBytes());

        StartProcessResponse spr = start(input);
        assertNotNull(spr.getInstanceId());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForStatus(processApi, spr.getInstanceId(), StatusEnum.FINISHED);

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*x=123.*", ab);
        assertLog(".*y=abc.*", ab);
        assertLog(".*z=" + zVal + ".*", ab);
        assertLog(".*myfile=" + myFileVal + ".*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testWorkDir() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("workDir").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);
        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello!", ab);
        assertLog(".*Bye!", ab);

        // ---

        ProcessFormsApi formResource = new ProcessFormsApi(getApiClient());
        List<FormListEntry> forms = formResource.list(pir.getInstanceId());
        assertEquals(1, forms.size());

        FormListEntry f = forms.get(0);
        FormSubmitResponse fsr = formResource.submit(pir.getInstanceId(), f.getName(), Collections.singletonMap("name", "test"));
        assertNull(fsr.getErrors());

        // ---

        pir = waitForStatus(processApi, spr.getInstanceId(), StatusEnum.FINISHED);
        ab = getLog(pir.getLogFileName());
        assertLogAtLeast(".*Hello!", 2, ab);
        assertLogAtLeast(".*Bye!", 2, ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testSwitch() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("switchCase").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*234234.*", ab);
        assertLog(".*Hello, Concord.*", ab);
        assertLog(".*Bye!.*", ab);
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
    public void testTags() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse parentSpr = start(payload);

        // ---

        waitForCompletion(processApi, parentSpr.getInstanceId());

        // ---

        payload = archive(ProcessIT.class.getResource("tags").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("parentInstanceId", parentSpr.getInstanceId().toString());
        StartProcessResponse childSpr = start(input);

        // ---

        waitForCompletion(processApi, childSpr.getInstanceId());

        // ---

        List<ProcessEntry> l = processApi.listSubprocesses(parentSpr.getInstanceId(), Collections.singletonList("abc"));
        assertTrue(l.isEmpty());

        l = processApi.listSubprocesses(parentSpr.getInstanceId(), Collections.singletonList("test"));
        assertEquals(1, l.size());

        ProcessEntry e = l.get(0);
        assertEquals(childSpr.getInstanceId(), e.getInstanceId());

        // ---

        l = processApi.list(null, null, null, null, null, Collections.singletonList("xyz"), null, null, null, 1, 0);
        assertTrue(l.isEmpty());

        l = processApi.list(null, null, null, null, null, Collections.singletonList("IT"), null, null, null, 1, 0);
        assertEquals(1, l.size());

        e = l.get(0);
        assertEquals(childSpr.getInstanceId(), e.getInstanceId());
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testProjectId() throws Exception {
        String orgName = "Default";
        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse cpr = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setAcceptsRawPayload(true));

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

        List<ProcessEntry> l = processApi.list(null, null, UUID.randomUUID(), null, null, null, null, null, null, 30, 0);
        assertTrue(l.isEmpty());

        l = processApi.list(null, null, cpr.getId(), null, null, null, null, null, null, 30, 0);
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
    public void testGetProcessForChildIds() throws Exception {

        // ---
        byte[] payload = archive(ProcessIT.class.getResource("processWithChildren").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse parentSpr = start(payload);

        // ---

        waitForCompletion(processApi, parentSpr.getInstanceId());

        ProcessEntry processEntry = processApi.get(parentSpr.getInstanceId());
        assertEquals(3, processEntry.getChildrenIds().size());
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
                .setAcceptsRawPayload(true));

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
                assertEquals(3, pe.getChildrenIds().size());
            }
        }
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testKillCascade() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("killCascade").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = start(input);
        ProcessApi processApi = new ProcessApi(getApiClient());

        waitForChild(processApi, spr.getInstanceId(), KindEnum.DEFAULT, StatusEnum.ENQUEUED, StatusEnum.PREPARING, StatusEnum.STARTING, StatusEnum.RUNNING);

        processApi.killCascade(spr.getInstanceId());

        waitForChild(processApi, spr.getInstanceId(), KindEnum.DEFAULT, StatusEnum.CANCELLED, StatusEnum.FINISHED, StatusEnum.FAILED);

        List<ProcessEntry> processEntryList = processApi.listSubprocesses(spr.getInstanceId(), null);
        for (ProcessEntry pe : processEntryList) {
            assertEquals(StatusEnum.CANCELLED, pe.getStatus());
        }
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testActiveProfiles() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("activeProfiles").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("activeProfiles", "profileA,profileB");

        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(StatusEnum.FINISHED, pe.getStatus());

        // ---

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*Hello from A\\+B.*", ab);
        assertLog(".*We got \\[profileA, profileB].*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testGetProcessErrorMessageFromRuntime() throws Exception {
        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("throwRuntime").toURI());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        assertEquals(StatusEnum.FAILED, pir.getStatus());
        assertProcessErrorMessage(pir, "BOOOM");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testGetProcessErrorMessageFromBpmnError() throws Exception {
        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("throwBpmnError").toURI());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        assertEquals(StatusEnum.FAILED, pir.getStatus());
        assertProcessErrorMessage(pir, "myBnpmError");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testInvalidEntryPointError() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("multipart").toURI());

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("entryPoint", "not-found");

        StartProcessResponse spr = start(input);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(StatusEnum.FAILED, pir.getStatus());

        assertProcessErrorMessage(pir, "Process 'not-found' not found");
    }

    @Test
    public void testFileUploadWithNonRootPath() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("fileupload").toURI());

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("target/file1", "test from file".getBytes());

        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(StatusEnum.FINISHED, pe.getStatus());

        // ---

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*test from file.*", ab);
    }

    @SuppressWarnings("unchecked")
    private static void assertProcessErrorMessage(ProcessEntry p, String expected) {
        assertNotNull(p);

        Map<String, Object> meta = p.getMeta();
        assertNotNull(meta);

        Map<String, Object> out = (Map<String, Object>) meta.get("out");
        assertNotNull(out);

        Map<String, Object> error = (Map<String, Object>) out.get(Constants.Context.LAST_ERROR_KEY);
        assertNotNull(error);

        assertEquals(expected, error.get("message"));
    }
}
