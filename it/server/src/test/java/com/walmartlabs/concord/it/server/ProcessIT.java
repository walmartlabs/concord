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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.client2.ProcessEntry.StatusEnum;
import com.walmartlabs.concord.client2.ProcessListFilter;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.PathUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.*;

public class ProcessIT extends AbstractServerIT {

    @Test
    @Disabled
    public void testLotsOfProcesses() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        int count = 100;
        for (int i = 0; i < count; i++) {
            start(payload);
        }
    }

    @Test
    public void testTimeout() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("timeout").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        try {
            processApi.waitForCompletion(spr.getInstanceId(), 3000L);
            fail("should fail");
        } catch (ApiException e) {
            String s = e.getResponseBody();
            ProcessEntry pir = getApiClient().getObjectMapper().readValue(s, ProcessEntry.class);
            assertTrue(StatusEnum.RUNNING.equals(pir.getStatus())
                    || StatusEnum.ENQUEUED.equals(pir.getStatus())
                    || StatusEnum.PREPARING.equals(pir.getStatus())
                    || StatusEnum.STARTING.equals(pir.getStatus()),
                    "Unexpected status: " + pir.getStatus());
        }

        processApi.kill(spr.getInstanceId());

        waitForStatus(getApiClient(), spr.getInstanceId(), StatusEnum.CANCELLED, StatusEnum.FAILED, StatusEnum.FINISHED);
    }

    @Test
    public void testTaskOut() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("taskOut").toURI());

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*I said: Hello!.*", ab);
    }

    @Test
    public void testDelegateOut() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("delegateOut").toURI());

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*I said: Hello!.*", ab);
    }

    @Test
    public void testProjectId() throws Exception {
        String orgName = "Default";
        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse por1 = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);
        StartProcessResponse sprA = start(input);
        waitForCompletion(getApiClient(), sprA.getInstanceId());

        // ---

        StartProcessResponse sprB = start(payload);
        waitForCompletion(getApiClient(), sprB.getInstanceId());

        // ---

        String anotherProjectName = "another_" + randomString();
        ProjectOperationResponse por2 = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(anotherProjectName));

        ProcessV2Api processV2Api = new ProcessV2Api(getApiClient());
        ProcessListFilter filter = ProcessListFilter.builder()
                .projectId(por2.getId())
                .limit(30)
                .build();

        List<ProcessEntry> l = processV2Api.listProcesses(filter);
        assertTrue(l.isEmpty());

        filter = ProcessListFilter.builder()
                .projectId(por1.getId())
                .limit(30)
                .build();

        l = processV2Api.listProcesses(filter);
        assertEquals(1, l.size());

        filter = ProcessListFilter.builder()
                .limit(30)
                .build();

        l = processV2Api.listProcesses(filter);
        ProcessEntry p = null;
        for (ProcessEntry e : l) {
            if (e.getInstanceId().equals(sprB.getInstanceId())) {
                p = e;
                break;
            }
        }
        assertNotNull(p);
    }

    @Test
    public void testGetAllProcessesForChildIds() throws Exception {
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
        ProjectOperationResponse por = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PRIVATE)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // grant the team access to the project

        projectsApi.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .teamId(ctr.getId())
                .orgName(orgName)
                .teamName(teamName)
                .level(ResourceAccessEntry.LevelEnum.READER));

        //Start a process with zero child

        byte[] payload = archive(ProcessIT.class.getResource("process").toURI());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse singleNodeProcess = start(input);
        waitForCompletion(getApiClient(), singleNodeProcess.getInstanceId());

        // Start a process with children

        payload = archive(ProcessIT.class.getResource("processWithChildren").toURI());
        input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse parentSpr = start(input);
        waitForCompletion(getApiClient(), parentSpr.getInstanceId());

        // ---
        ProcessListFilter filter = ProcessListFilter.builder()
                .projectId(por.getId())
                .limit(10)
                .addInclude(ProcessDataInclude.CHILDREN_IDS)
                .build();
        List<ProcessEntry> processEntry = new ProcessV2Api(getApiClient()).listProcesses(filter);
        for (ProcessEntry pe : processEntry) {
            if (pe.getInstanceId().equals(singleNodeProcess.getInstanceId())) {
                assertTrue(pe.getChildrenIds() == null || pe.getChildrenIds().isEmpty());
            } else if (pe.getInstanceId().equals(parentSpr.getInstanceId())) {
                assertEquals(3, pe.getChildrenIds() != null ? pe.getChildrenIds().size() : 0);
            }
        }
    }

    @Test
    public void testForkInitiatorFromApiKey() throws Exception {
        // add the user A
        UsersApi usersApi = new UsersApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest().username(userAName).type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKeyA = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userAName));

        String yaml = new String(Files.readAllBytes(Paths.get(ProcessIT.class.getResource("forkInitiator").toURI()).resolve("concord.yml")));
        yaml = yaml.replace("{{apiKey}}", apiKeyA.getKey());

        Path tmp = Files.createTempDirectory("concord-it-fork");
        Files.write(tmp.resolve("concord.yml"), yaml.getBytes());

        byte[] payload = archive(tmp.toUri());
        PathUtils.deleteRecursively(tmp);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse parentSpr = start(input);

        ProcessEntry processEntry = waitForCompletion(getApiClient(), parentSpr.getInstanceId());
        assertEquals(StatusEnum.FINISHED, processEntry.getStatus(), () -> {
            try {
                return new String(getLog(processEntry.getInstanceId()));
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(1, processEntry.getChildrenIds().size());

        ProcessEntry child = waitForCompletion(getApiClient(), processEntry.getChildrenIds().iterator().next());
        assertEquals(StatusEnum.FINISHED, child.getStatus());

        {
            byte[] ab = getLog(processEntry.getInstanceId());
            assertLog(".*initiator: .*admin.*", ab);
        }
        {
            byte[] ab = getLog(child.getInstanceId());
            assertLog(".*initiator: .*" + userAName.toLowerCase() + ".*", ab);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEffectiveYaml() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("effectiveYaml").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start("test", payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(StatusEnum.FINISHED, pir.getStatus());

        try (InputStream effectiveYaml = processApi.downloadStateFile(pir.getInstanceId(), ".concord/effective.concord.yml")) {
            assertNotNull(effectiveYaml);

            Map<String, Object> m = new ObjectMapper(new YAMLFactory()).readValue(effectiveYaml, Map.class);
            String entryPoint = (String) ConfigurationUtils.get(m, "configuration", "entryPoint");
            assertEquals("test", entryPoint);
        }
    }
}
