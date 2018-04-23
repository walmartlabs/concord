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
import com.walmartlabs.concord.server.api.org.*;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.ProjectOperationResponse;
import com.walmartlabs.concord.server.api.org.project.ProjectResource;
import com.walmartlabs.concord.server.api.org.project.ProjectVisibility;
import com.walmartlabs.concord.server.api.org.team.*;
import com.walmartlabs.concord.server.api.process.*;
import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.user.CreateUserRequest;
import com.walmartlabs.concord.server.api.user.UserResource;
import com.walmartlabs.concord.server.api.user.UserType;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.ForbiddenException;
import java.util.*;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.*;

@RunWith(ParallelRunner.class)
public class RunAsIT extends AbstractServerIT {

    @Test(timeout = 60000)
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

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.updateAccessLevel(orgName, projectName, new ResourceAccessEntry(teamId, orgName, teamName, ResourceAccessLevel.READER));

        // Start a process

        byte[] payload = archive(RunAsIT.class.getResource("runas").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse p = start(input);
        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pe = waitForStatus(processResource, p.getInstanceId(), ProcessStatus.SUSPENDED);

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*username=" + userAName + ".*==.*username=" + userAName + ".*", ab);

        String formId = findForm(p.getInstanceId());

        FormResource formResource = proxy(FormResource.class);

        Map<String, Object> data = Collections.singletonMap("firstName", "xxx");

        // try submit as a wrong user

        try {
            formResource.submit(p.getInstanceId(), formId, data);
            fail("exception expected");
        } catch (ForbiddenException e) {
            // ignore
        }

        // submit as the proper user

        resetApiKey();

        FormSubmitResponse fsr = formResource.submit(p.getInstanceId(), formId, data);
        assertTrue(fsr.isOk());

        pe = waitForCompletion(processResource, p.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pe.getStatus());

        ab = getLog(pe.getLogFileName());
        assertLog(".*Now we are running as admin. Initiator: " + userAName + ".*", ab);
    }

    @Test(timeout = 60000)
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

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pe = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*AAA: " + userAName + ".*", ab);

        // submit the form

        setApiKey(apiKeyB.getKey());

        String formId = findForm(pe.getInstanceId());

        FormResource formResource = proxy(FormResource.class);
        Map<String, Object> data = Collections.singletonMap("msg", "Hello!");
        formResource.submit(pe.getInstanceId(), formId, data);

        // wait for the process to finish

        pe = waitForCompletion(processResource, spr.getInstanceId());

        // check the logs

        ab = getLog(pe.getLogFileName());
        assertLog(".*BBB: Hello!.*", ab);
        assertLog(".*CCC: " + userAName + ".*", ab);
    }

    private void createOrg(String orgName) {
        OrganizationResource orgResource = proxy(OrganizationResource.class);
        CreateOrganizationResponse r = orgResource.createOrUpdate(new OrganizationEntry(orgName));
        assertTrue(r.isOk());
    }

    private UUID createTeam(String orgName, String teamName, String userName) {
        TeamResource teamResource = proxy(TeamResource.class);
        CreateTeamResponse ctr = teamResource.createOrUpdate(orgName, new TeamEntry(teamName));

        teamResource.addUsers(orgName, teamName, Collections.singleton(new TeamUserEntry(userName, TeamRole.MEMBER)));

        return ctr.getId();
    }

    private CreateApiKeyResponse addUser(String userAName) {
        UserResource userResource = proxy(UserResource.class);

        userResource.createOrUpdate(new CreateUserRequest(userAName, UserType.LOCAL));

        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);
        return apiKeyResource.create(new CreateApiKeyRequest(userAName));
    }

    private void createProject(String orgName, String projectName) {
        ProjectResource projectResource = proxy(ProjectResource.class);
        ProjectOperationResponse por = projectResource.createOrUpdate(orgName, new ProjectEntry(projectName, ProjectVisibility.PRIVATE));
        assertTrue(por.isOk());
    }

    private String findForm(UUID instanceId) {
        FormResource formResource = proxy(FormResource.class);

        List<FormListEntry> forms = formResource.list(instanceId);
        assertEquals(1, forms.size());

        // ---

        FormListEntry f0 = forms.get(0);
        assertFalse(f0.isCustom());

        return f0.getFormInstanceId();
    }
}
