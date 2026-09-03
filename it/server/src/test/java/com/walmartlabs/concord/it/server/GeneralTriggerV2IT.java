package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import com.walmartlabs.concord.common.PathUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class GeneralTriggerV2IT extends AbstractGeneralTriggerIT {

    private String orgName;
    private String projectName;
    private String repoName;
    private OrganizationsApi orgApi;

    private void setup(String yamlPath) throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(TriggersRefreshIT.class.getResource(yamlPath).toURI());
        PathUtils.copy(src.toPath(), tmpDir);

        try (Git repo = Git.init().setInitialBranch("master").setDirectory(tmpDir.toFile()).call()) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();
        }

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        orgName = "org_" + randomString();
        projectName = "project_" + randomString();
        repoName = "repo_" + randomString();

        orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .url(gitUrl)
                        .branch("master"))));
    }

    private void cleanup() throws ApiException {
        orgApi.deleteOrg(orgName, "yes");
    }

    @Test
    public void testExclusiveV2() throws Exception {
        setup("generalExclusiveTriggerv2");

        waitForTriggers(orgName, projectName, repoName, 2);

        ExternalEventsApi eea = new ExternalEventsApi(getApiClient());
        Map<String, Object> eventParam = new HashMap<>();
        eventParam.put("key1", "value2");

        // first process
        eea.externalEvent("testTriggerv2", eventParam);

        // second process
        eea.externalEvent("testTriggerv2", eventParam);

        Map<ProcessEntry.StatusEnum, ProcessEntry> ps = waitProcesses(orgName, projectName, ProcessEntry.StatusEnum.FINISHED, ProcessEntry.StatusEnum.CANCELLED);
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.FINISHED), ".*Hello from exclusive trigger v2.*");
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.CANCELLED), ".*Process\\(es\\) with exclusive group 'RED' is already in the queue. Current process has been cancelled.*");

        cleanup();
    }

    @Test
    public void testExclusiveFromConfigurationV2() throws Exception {
        setup("generalTriggerWithExclusiveCfgv2");

        waitForTriggers(orgName, projectName, repoName, 2);

        // ---

        ExternalEventsApi eea = new ExternalEventsApi(getApiClient());
        Map<String, Object> eventParam = new HashMap<>();
        eventParam.put("key1", "value2");

        // first process
        eea.externalEvent("testTriggerv2", eventParam);

        // second process
        eea.externalEvent("testTriggerv2", eventParam);

        Map<ProcessEntry.StatusEnum, ProcessEntry> ps = waitProcesses(orgName, projectName, ProcessEntry.StatusEnum.FINISHED, ProcessEntry.StatusEnum.CANCELLED);
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.FINISHED), ".*Hello from exclusive trigger v2.*");
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.CANCELLED), ".*Process\\(es\\) with exclusive group 'RED' is already in the queue. Current process has been cancelled.*");

        // ---

        cleanup();
    }

    @Test
    public void testExclusiveWithTriggerOverrideV2() throws Exception {
        setup("generalTriggerWithExclusiveOverridev2");

        waitForTriggers(orgName, projectName, repoName, 2);

        // ---

        ExternalEventsApi eea = new ExternalEventsApi(getApiClient());
        Map<String, Object> eventParam = new HashMap<>();
        eventParam.put("key1", "value2");

        // first process
        eea.externalEvent("testTriggerv2", eventParam);

        // second process
        eea.externalEvent("testTriggerv2", eventParam);

        Map<ProcessEntry.StatusEnum, ProcessEntry> ps = waitProcesses(orgName, projectName, ProcessEntry.StatusEnum.FINISHED, ProcessEntry.StatusEnum.CANCELLED);
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.FINISHED), ".*Hello from exclusive trigger v2.*");
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.CANCELLED), ".*Process\\(es\\) with exclusive group 'TRIGGER' is already in the queue. Current process has been cancelled.*");

        // ---

        cleanup();
    }

    // --- authorization tests ---

    @Test
    public void testListTriggersNoOrgRejected() throws Exception {
        // calling without orgId or orgName must be rejected
        TriggersV2Api triggersApi = new TriggersV2Api(getApiClient());
        try {
            triggersApi.listTriggersV2(null, null, null, null, null, null, null);
            fail("Should fail");
        } catch (ApiException e) {
            assertEquals(400, e.getCode());
        }
    }

    @Test
    public void testListTriggersRepoNameWithoutProjectRejected() throws Exception {
        // a repoName query parameter requires a project to scope it
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        try {
            TriggersV2Api triggersApi = new TriggersV2Api(getApiClient());
            try {
                triggersApi.listTriggersV2(null, null, orgName, null, null, null, "some-repo");
                fail("Should fail");
            } catch (ApiException e) {
                assertEquals(400, e.getCode());
            }
        } finally {
            orgApi.deleteOrg(orgName, "yes");
        }
    }

    @Test
    public void testListTriggersPrivateProjectRequiresAccess() throws Exception {
        // a user with no project access must be denied when querying a specific private project
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse projectResponse = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name("project_" + randomString())
                .visibility(ProjectEntry.VisibilityEnum.PRIVATE));
        UUID projectId = projectResponse.getId();

        UsersApi usersApi = new UsersApi(getApiClient());
        String userName = "user_" + randomString();
        CreateUserResponse cur = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse car = apiKeysApi.createUserApiKey(new CreateApiKeyRequest().userId(cur.getId()));

        setApiKey(car.getKey());

        try {
            TriggersV2Api triggersApi = new TriggersV2Api(getApiClient());
            try {
                triggersApi.listTriggersV2(null, null, orgName, projectId, null, null, null);
                fail("Should fail");
            } catch (ApiException e) {
                assertEquals(403, e.getCode());
            }
        } finally {
            resetApiKey();
            orgApi.deleteOrg(orgName, "yes");
        }
    }

    @Test
    public void testListTriggersOrgMemberCanQueryOrgScope() throws Exception {
        // an org member listing triggers without specifying a project must succeed
        String orgName = "org_" + randomString();
        String teamName = "team_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        UsersApi usersApi = new UsersApi(getApiClient());
        String userName = "user_" + randomString();
        CreateUserResponse cur = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse car = apiKeysApi.createUserApiKey(new CreateApiKeyRequest().userId(cur.getId()));

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));
        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .userId(cur.getId())
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        setApiKey(car.getKey());

        try {
            TriggersV2Api triggersApi = new TriggersV2Api(getApiClient());
            triggersApi.listTriggersV2(null, null, orgName, null, null, null, null);
        } finally {
            resetApiKey();
            orgApi.deleteOrg(orgName, "yes");
        }
    }
}
