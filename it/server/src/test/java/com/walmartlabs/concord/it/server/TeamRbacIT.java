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
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(ParallelRunner.class)
public class TeamRbacIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void testOrgs() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgAName = "orgA_" + randomString();
        CreateOrganizationResponse orgA = orgApi.createOrUpdate(new OrganizationEntry().setName(orgAName));

        String orgBName = "orgB_" + randomString();
        CreateOrganizationResponse orgB = orgApi.createOrUpdate(new OrganizationEntry().setName(orgBName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String teamAName = "teamA_" + randomString();
        teamsApi.createOrUpdate(orgAName, new TeamEntry().setName(teamAName));

        String teamBName = "teamB_" + randomString();
        teamsApi.createOrUpdate(orgBName, new TeamEntry().setName(teamBName));

        // ---

        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userAName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest()
                .setUsername(userAName)
                .setUserType(CreateApiKeyRequest.UserTypeEnum.LOCAL));

        teamsApi.addUsers(orgAName, teamAName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userAName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));

        String userBName = "userB_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userBName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyB = apiKeyResource.create(new CreateApiKeyRequest()
                .setUsername(userBName)
                .setUserType(CreateApiKeyRequest.UserTypeEnum.LOCAL));

        teamsApi.addUsers(orgBName, teamBName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userBName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        setApiKey(apiKeyA.getKey());

        String projectAName = "projectA_" + randomString();
        projectsApi.createOrUpdate(orgAName, new ProjectEntry().setName(projectAName));

        try {
            String projectBName = "projectB_" + randomString();
            projectsApi.createOrUpdate(orgBName, new ProjectEntry().setName(projectBName));
            fail("should fail");
        } catch (ApiException e) {
        }

        setApiKey(apiKeyB.getKey());

        String projectBName = "projectB_" + randomString();
        projectsApi.createOrUpdate(orgBName, new ProjectEntry().setName(projectBName));
    }

    @Test(timeout = 60000)
    public void testTeamCreators() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "orgA_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String teamAName = "teamA_" + randomString();
        teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamAName));

        // ---

        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest().setUsername(userAName).setType(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest()
                .setUsername(userAName)
                .setUserType(CreateApiKeyRequest.UserTypeEnum.LOCAL));

        // ---

        setApiKey(apiKeyA.getKey());

        try {
            teamsApi.createOrUpdate(orgName, new TeamEntry()
                    .setName(teamAName)
                    .setDescription("test"));
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();

        teamsApi.addUsers(orgName, teamAName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userAName)
                .setRole(TeamUserEntry.RoleEnum.MAINTAINER)));

        // ---

        setApiKey(apiKeyA.getKey());

        teamsApi.createOrUpdate(orgName, new TeamEntry()
                .setName(teamAName)
                .setDescription("test"));

        // ---

        String teamBName = "teamB_" + randomString();

        try {
            teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamBName));
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();

        teamsApi.addUsers(orgName, teamAName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userAName)
                .setRole(TeamUserEntry.RoleEnum.OWNER)));

        // ---

        teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamBName));
    }

    @Test(timeout = 60000)
    public void testTeamMaintainers() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "orgA_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String teamName = "teamA_" + randomString();
        teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));

        // ---

        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest().setUsername(userAName).setType(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));

        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userAName)
                .setRole(TeamUserEntry.RoleEnum.MAINTAINER)));

        String userBName = "userB_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userBName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyB = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userBName));

        // ---

        setApiKey(apiKeyB.getKey());

        try {
            teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                    .setUsername(userBName)
                    .setRole(TeamUserEntry.RoleEnum.MEMBER)));
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        setApiKey(apiKeyA.getKey());
        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userBName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));
    }

    @Test(timeout = 60000)
    public void testNewTeamOwner() throws Exception {
        String userA = "userA_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());

        usersApi.createOrUpdate(new CreateUserRequest().setUsername(userA).setType(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse userAKey = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userA));

        String userB = "userA_" + randomString();

        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userB)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse userBKey = apiKeyResource.create(new CreateApiKeyRequest()
                .setUsername(userB));

        // ---

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "orgA_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.addUsers(orgName, "default", false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userA)
                .setRole(TeamUserEntry.RoleEnum.OWNER)));

        // ---

        setApiKey(userBKey.getKey());

        // ---

        String teamName = "teamA_" + randomString();
        try {
            teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        setApiKey(userAKey.getKey());

        // ---

        teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));
        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userB)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));
    }

    @Test(timeout = 60000)
    public void testTeamDelete() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "orgA_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String teamName = "teamA_" + randomString();
        teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));

        // ---

        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername("admin")
                .setRole(TeamUserEntry.RoleEnum.OWNER)));

        // ---

        List<TeamEntry> l = teamsApi.list(orgName);
        assertEquals(2, l.size());

        // ---

        teamsApi.delete(orgName, teamName);

        // ---

        l = teamsApi.list(orgName);
        assertEquals(1, l.size());
    }

    @Test(timeout = 60000)
    public void testOrgProjects() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "orgA_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String teamName = "teamA_" + randomString();
        CreateTeamResponse ctr = teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));

        // ---

        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest().setUsername(userAName).setType(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));

        String userBName = "userB_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userBName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyB = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userBName));

        // ---

        setApiKey(apiKeyA.getKey());

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String projectName = "projectA_" + randomString();
        try {
            projectsApi.createOrUpdate(orgName, new ProjectEntry().setName(projectName));
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();
        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userAName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        setApiKey(apiKeyA.getKey());
        projectsApi.createOrUpdate(orgName, new ProjectEntry().setName(projectName));

        // ---

        setApiKey(apiKeyB.getKey());

        try {
            projectsApi.createOrUpdate(orgName, new ProjectEntry()
                    .setName(projectName)
                    .setDescription("new description")
                    .setAcceptsRawPayload(true));
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        setApiKey(apiKeyA.getKey());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setDescription("new description")
                .setAcceptsRawPayload(true));

        // ---

        setApiKey(apiKeyA.getKey());
        projectsApi.updateAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .setTeamId(ctr.getId())
                .setOrgName(orgName)
                .setTeamName(teamName)
                .setLevel(ResourceAccessEntry.LevelEnum.WRITER));

        // ---

        setApiKey(apiKeyB.getKey());

        try {
            projectsApi.createOrUpdate(orgName, new ProjectEntry()
                    .setName(projectName)
                    .setDescription("another description")
                    .setAcceptsRawPayload(true));
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();
        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userBName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        setApiKey(apiKeyB.getKey());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setDescription("another description")
                .setAcceptsRawPayload(true));
    }

    @Test(timeout = 60000)
    public void testOrgPublicSecrets() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgAName = "orgA_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgAName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String teamAName = "teamA_" + randomString();
        teamsApi.createOrUpdate(orgAName, new TeamEntry().setName(teamAName));

        // ---

        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest().setUsername(userAName).setType(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));

        String userBName = "userB_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userBName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyB = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userBName));

        // ---

        setApiKey(apiKeyA.getKey());

        String secretAName = "secretA_" + randomString();
        try {
            generateKeyPair(orgAName, secretAName, false, null);
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();
        teamsApi.addUsers(orgAName, teamAName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userAName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        setApiKey(apiKeyA.getKey());
        generateKeyPair(orgAName, secretAName, false, null);

        // ---

        SecretsApi secretResource = new SecretsApi(getApiClient());

        setApiKey(apiKeyB.getKey());
        secretResource.getPublicKey(orgAName, secretAName);

        // ---

        setApiKey(apiKeyB.getKey());

        try {
            secretResource.delete(orgAName, secretAName);
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        setApiKey(apiKeyA.getKey());
        secretResource.delete(orgAName, secretAName);
    }

    @Test(timeout = 60000)
    public void testInventory() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String teamName = "teamA_" + randomString();

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));

        // ---

        String inventoryName = "inv_" + randomString();

        InventoriesApi inventoryResource = new InventoriesApi(getApiClient());
        inventoryResource.createOrUpdate(orgName, new InventoryEntry()
                .setName(inventoryName)
                .setVisibility(InventoryEntry.VisibilityEnum.PRIVATE));

        // ---

        inventoryResource.updateAccessLevel(orgName, inventoryName, new ResourceAccessEntry()
                .setOrgName(orgName)
                .setTeamName(teamName)
                .setLevel(ResourceAccessEntry.LevelEnum.READER));

        // ---

        String userAName = "userA_" + randomString();
        String userBName = "userB_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdate(new CreateUserRequest().setUsername(userAName).setType(CreateUserRequest.TypeEnum.LOCAL));
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userBName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));

        // ---

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakrA = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));
        CreateApiKeyResponse cakrB = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userBName));

        // ---

        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userAName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        setApiKey(cakrA.getKey());

        inventoryResource.get(orgName, inventoryName);

        // ---

        setApiKey(cakrB.getKey());

        try {
            inventoryResource.get(orgName, inventoryName);
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();

        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userBName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        setApiKey(cakrB.getKey());

        inventoryResource.get(orgName, inventoryName);
    }

    @Test(timeout = 60000)
    public void testTeamUsersUpsert() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String teamName = "team_" + randomString();

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));

        // ---

        String userName = "user_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));

        // ---

        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));
        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userName)
                .setRole(TeamUserEntry.RoleEnum.MAINTAINER)));
    }

    @Test(timeout = 60000)
    public void testSecretAccessLevels() throws Exception {
        SecretsApi secretResource = new SecretsApi(getApiClient());

        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String secretName = "secret_" + randomString();
        addPlainSecret(orgName, secretName, false, null, new byte[]{0, 1, 2});
        secretResource.update(orgName, secretName, new SecretUpdateRequest().setVisibility(SecretUpdateRequest.VisibilityEnum.PRIVATE));

        // ---

        String userName = "user_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userName));

        // ---

        setApiKey(cakr.getKey());

        try {
            secretResource.get(orgName, secretName);
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();

        // ---

        String teamName = "team_" + randomString();

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));

        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        setApiKey(cakr.getKey());

        try {
            secretResource.get(orgName, secretName);
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();

        secretResource.updateAccessLevel(orgName, secretName, new ResourceAccessEntry()
                .setTeamName(teamName)
                .setLevel(ResourceAccessEntry.LevelEnum.READER));

        // ---

        setApiKey(cakr.getKey());

        SecretEntry s = secretResource.get(orgName, secretName);
        assertEquals(secretName, s.getName());

        try {
            secretResource.delete(orgName, secretName);
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();

        secretResource.updateAccessLevel(orgName, secretName, new ResourceAccessEntry()
                .setTeamName(teamName)
                .setLevel(ResourceAccessEntry.LevelEnum.WRITER));

        // ---

        setApiKey(cakr.getKey());

        GenericOperationResult r = secretResource.delete(orgName, secretName);
        assertEquals(GenericOperationResult.ResultEnum.DELETED, r.getResult());
    }
}
