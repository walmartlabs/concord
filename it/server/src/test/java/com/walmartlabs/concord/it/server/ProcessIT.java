package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.googlecode.junittoolbox.ParallelRunner;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.OrganizationResource;
import com.walmartlabs.concord.server.api.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.api.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.ProjectOperationResponse;
import com.walmartlabs.concord.server.api.org.project.ProjectVisibility;
import com.walmartlabs.concord.server.api.org.team.*;
import com.walmartlabs.concord.server.api.process.*;
import com.walmartlabs.concord.server.api.project.CreateProjectResponse;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.user.CreateUserRequest;
import com.walmartlabs.concord.server.api.user.UserResource;
import com.walmartlabs.concord.server.api.user.UserType;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.util.*;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.*;

@RunWith(ParallelRunner.class)
public class ProcessIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testUploadAndRun() throws Exception {
        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        // start the process

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, world.*", ab);
        assertLog(".*Hello, local files!.*", ab);
    }

    @Test(timeout = 30000)
    public void testDefaultEntryPoint() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("defaultEntryPoint").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, Concord!.*", ab);
    }

    @Test
    @Ignore
    public void testLotsOfProcesses() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        int count = 100;
        for (int i = 0; i < count; i++) {
            ProcessResource processResource = proxy(ProcessResource.class);
            processResource.start(new ByteArrayInputStream(payload), null, false, null);
        }
    }

    @Test(timeout = 30000)
    public void testUploadAndRunSync() throws Exception {
        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("process-sync").toURI());

        // start the process

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, true, null);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = processResource.get(spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*110123.*", ab);
        assertLog(".*Boo Zoo.*", ab);
        assertLog(".*100022.*", ab);
        assertLog(".*120123.*", ab);
        assertLog(".*red.*", ab);

        assertTrue(pir.getStatus() == ProcessStatus.FINISHED);
    }

    @Test(timeout = 60000)
    public void testTimeout() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("timeout").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        try {
            processResource.waitForCompletion(spr.getInstanceId(), 3000);
            fail("should fail");
        } catch (WebApplicationException e) {
            Response r = e.getResponse();
            ProcessEntry pir = r.readEntity(ProcessEntry.class);
            assertEquals(ProcessStatus.RUNNING, pir.getStatus());
        }

        processResource.kill(spr.getInstanceId());

        waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.CANCELLED, ProcessStatus.FAILED, ProcessStatus.FINISHED);
    }

    @Test(timeout = 30000)
    public void testInterpolation() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("interpolation").toURI());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);
        assertNotNull(spr.getInstanceId());

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, world.*", ab);
    }

    @Test(timeout = 30000)
    public void testErrorHandling() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("errorHandling").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Kaboom.*", ab);
        assertLog(".*We got:.*java.lang.RuntimeException.*", ab);
    }

    @Test(timeout = 30000)
    public void testStartupProblem() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("startupProblem").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.FAILED);

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*gaaarbage.*", ab);
    }

    @Test(timeout = 30000)
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

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.FINISHED);

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*x=123.*", ab);
        assertLog(".*y=abc.*", ab);
        assertLog(".*z=" + zVal + ".*", ab);
        assertLog(".*myfile=" + myFileVal + ".*", ab);
    }

    @Test(timeout = 30000)
    public void testWorkDir() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("workDir").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);
        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello!", ab);
        assertLog(".*Bye!", ab);

        // ---

        FormResource formResource = proxy(FormResource.class);
        List<FormListEntry> forms = formResource.list(pir.getInstanceId());
        assertEquals(1, forms.size());

        FormListEntry f = forms.get(0);
        FormSubmitResponse fsr = formResource.submit(pir.getInstanceId(), f.getFormInstanceId(), Collections.singletonMap("name", "test"));
        assertNull(fsr.getErrors());

        // ---

        pir = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.FINISHED);
        ab = getLog(pir.getLogFileName());
        assertLogAtLeast(".*Hello!", 2, ab);
        assertLogAtLeast(".*Bye!", 2, ab);
    }

    @Test(timeout = 30000)
    public void testSwitch() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("switchCase").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*234234.*", ab);
        assertLog(".*Hello, Concord.*", ab);
        assertLog(".*Bye!.*", ab);
    }

    @Test(timeout = 30000)
    public void testTaskOut() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("taskOut").toURI(), ITConstants.DEPENDENCIES_DIR);

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*I said: Hello!.*", ab);
    }

    @Test(timeout = 30000)
    public void testDelegateOut() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("delegateOut").toURI(), ITConstants.DEPENDENCIES_DIR);

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*I said: Hello!.*", ab);
    }

    @Test(timeout = 30000)
    public void testTags() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse parentSpr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        // ---

        waitForCompletion(processResource, parentSpr.getInstanceId());

        // ---

        payload = archive(ProcessIT.class.getResource("tags").toURI());
        StartProcessResponse childSpr = processResource.start(new ByteArrayInputStream(payload), parentSpr.getInstanceId(), false, null);

        // ---

        waitForCompletion(processResource, childSpr.getInstanceId());

        // ---

        List<ProcessEntry> l = processResource.list(parentSpr.getInstanceId(), Collections.singleton("abc"));
        assertTrue(l.isEmpty());

        l = processResource.list(parentSpr.getInstanceId(), Collections.singleton("test"));
        assertEquals(1, l.size());

        ProcessEntry e = l.get(0);
        assertEquals(childSpr.getInstanceId(), e.getInstanceId());

        // ---

        l = processResource.list(null, null, Collections.singleton("xyz"), 1);
        assertTrue(l.isEmpty());

        l = processResource.list(null, null, Collections.singleton("IT"), 1);
        assertEquals(1, l.size());

        e = l.get(0);
        assertEquals(childSpr.getInstanceId(), e.getInstanceId());
    }

    @Test(timeout = 30000)
    public void testProjectId() throws Exception {
        String projectName = "project_" + randomString();

        ProjectResource projectResource = proxy(ProjectResource.class);
        CreateProjectResponse cpr = projectResource.createOrUpdate(new ProjectEntry(projectName));

        String entryPoint = projectName;

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse sprA = processResource.start(entryPoint, new ByteArrayInputStream(payload), null, false, null);
        waitForCompletion(processResource, sprA.getInstanceId());

        // ---

        StartProcessResponse sprB = processResource.start(new ByteArrayInputStream(payload), null, false, null);
        waitForCompletion(processResource, sprB.getInstanceId());

        // ---

        List<ProcessEntry> l = processResource.list(UUID.randomUUID(), null, null, 30);
        assertTrue(l.isEmpty());

        l = processResource.list(cpr.getId(), null, null, 30);
        assertEquals(1, l.size());

        l = processResource.list(null, null, null, 30);
        ProcessEntry p = null;
        for (ProcessEntry e : l) {
            if (e.getInstanceId().equals(sprB.getInstanceId())) {
                p = e;
                break;
            }
        }
        assertNotNull(p);
    }

    @Test(timeout = 60000)
    public void testGetProcessForChildIds() throws Exception {

        // ---
        byte[] payload = archive(ProcessIT.class.getResource("processWithChildren").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse parentSpr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        // ---

        waitForCompletion(processResource, parentSpr.getInstanceId());

        ProcessEntry processEntry = processResource.get(parentSpr.getInstanceId());
        assertEquals(3, processEntry.getChildrenIds().size());
    }

    @Test(timeout = 60000)
    public void testGetAllProcessesForChildIds() throws Exception {
        // create a new org

        String orgName = "org_" + randomString();

        OrganizationResource orgResource = proxy(OrganizationResource.class);
        orgResource.createOrUpdate(new OrganizationEntry(orgName));

        // add the user A

        UserResource userResource = proxy(UserResource.class);

        String userAName = "userA_" + randomString();
        userResource.createOrUpdate(new CreateUserRequest(userAName, UserType.LOCAL));

        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest(userAName));

        // create the user A's team

        String teamName = "team_" + randomString();

        TeamResource teamResource = proxy(TeamResource.class);
        CreateTeamResponse ctr = teamResource.createOrUpdate(orgName, new TeamEntry(teamName));

        teamResource.addUsers(orgName, teamName, false, Collections.singleton(new TeamUserEntry(userAName, TeamRole.MEMBER)));

        // switch to the user A and create a new private project

        setApiKey(apiKeyA.getKey());

        String projectName = "project_" + randomString();

        com.walmartlabs.concord.server.api.org.project.ProjectResource projectResource = proxy(com.walmartlabs.concord.server.api.org.project.ProjectResource.class);
        ProjectOperationResponse por = projectResource.createOrUpdate(orgName, new ProjectEntry(projectName, ProjectVisibility.PRIVATE));

        // grant the team access to the project

        projectResource.updateAccessLevel(orgName, projectName, new ResourceAccessEntry(ctr.getId(), orgName, teamName, ResourceAccessLevel.READER));

        //Start a process with zero child

        byte[] payload = archive(ProcessIT.class.getResource("process").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse singleNodeProcess = start(input);
        ProcessResource processResource = proxy(ProcessResource.class);
        waitForCompletion(processResource, singleNodeProcess.getInstanceId());

        // Start a process with children

        payload = archive(ProcessIT.class.getResource("processWithChildren").toURI());
        input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse parentSpr = start(input);
        waitForCompletion(processResource, parentSpr.getInstanceId());

        // ---

        List<ProcessEntry> processEntry = processResource.list(por.getId(), null, null, 10);
        for (ProcessEntry pe : processEntry) {
            if (pe.getInstanceId().equals(singleNodeProcess.getInstanceId())) {
                assertTrue(pe.getChildrenIds().isEmpty());
            } else if (pe.getInstanceId().equals(parentSpr.getInstanceId())) {
                assertEquals(3, pe.getChildrenIds().size());
            }
        }
    }

    @Test(timeout = 30000)
    public void testKillCascade() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("killCascade").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = start(input);
        ProcessResource processResource = proxy(ProcessResource.class);

        waitForChild(processResource, spr.getInstanceId(), ProcessKind.DEFAULT, ProcessStatus.RUNNING);

        processResource.killCascade(spr.getInstanceId());

        waitForChild(processResource, spr.getInstanceId(), ProcessKind.DEFAULT, ProcessStatus.CANCELLED,ProcessStatus.FINISHED,ProcessStatus.FAILED);

        List<ProcessEntry> processEntryList = processResource.list(spr.getInstanceId(), null);
        for (ProcessEntry pe : processEntryList){
            assertEquals(ProcessStatus.CANCELLED, pe.getStatus());
        }


    }
}
