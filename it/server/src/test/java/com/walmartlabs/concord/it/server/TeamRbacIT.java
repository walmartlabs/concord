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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TeamRbacIT extends AbstractServerIT {

    @Test
    public void testOrgs() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgAName = "orgA_" + randomString();
        CreateOrganizationResponse orgA = orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgAName));

        String orgBName = "orgB_" + randomString();
        CreateOrganizationResponse orgB = orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgBName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String teamAName = "teamA_" + randomString();
        teamsApi.createOrUpdateTeam(orgAName, new TeamEntry().name(teamAName));

        String teamBName = "teamB_" + randomString();
        teamsApi.createOrUpdateTeam(orgBName, new TeamEntry().name(teamBName));

        // ---

        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userAName)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.createUserApiKey(new CreateApiKeyRequest()
                .username(userAName)
                .userType(CreateApiKeyRequest.UserTypeEnum.LOCAL));

        teamsApi.addUsersToTeam(orgAName, teamAName, false, Collections.singletonList(new TeamUserEntry()
                .username(userAName)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        String userBName = "userB_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userBName)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyB = apiKeyResource.createUserApiKey(new CreateApiKeyRequest()
                .username(userBName)
                .userType(CreateApiKeyRequest.UserTypeEnum.LOCAL));

        teamsApi.addUsersToTeam(orgBName, teamBName, false, Collections.singletonList(new TeamUserEntry()
                .username(userBName)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        // ---


        setApiKey(apiKeyA.getKey());

        String projectAName = "projectA_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgAName, new ProjectEntry().name(projectAName));

        try {
            String projectBName = "projectB_" + randomString();
            projectsApi.createOrUpdateProject(orgBName, new ProjectEntry().name(projectBName));
            fail("should fail");
        } catch (ApiException e) {
        }

        setApiKey(apiKeyB.getKey());

        projectsApi = new ProjectsApi(getApiClient());
        String projectBName = "projectB_" + randomString();
        projectsApi.createOrUpdateProject(orgBName, new ProjectEntry().name(projectBName));
    }

    @Test
    public void testTeamCreators() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "orgA_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String teamAName = "teamA_" + randomString();
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamAName));

        // ---

        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest().username(userAName).type(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.createUserApiKey(new CreateApiKeyRequest()
                .username(userAName)
                .userType(CreateApiKeyRequest.UserTypeEnum.LOCAL));

        // ---

        setApiKey(apiKeyA.getKey());

        try {
            teamsApi.createOrUpdateTeam(orgName, new TeamEntry()
                    .name(teamAName)
                    .description("test"));
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();

        teamsApi.addUsersToTeam(orgName, teamAName, false, Collections.singletonList(new TeamUserEntry()
                .username(userAName)
                .role(TeamUserEntry.RoleEnum.MAINTAINER)));

        // ---

        setApiKey(apiKeyA.getKey());

        teamsApi.createOrUpdateTeam(orgName, new TeamEntry()
                .name(teamAName)
                .description("test"));

        // ---

        String teamBName = "teamB_" + randomString();

        try {
            teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamBName));
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();

        teamsApi.addUsersToTeam(orgName, teamAName, false, Collections.singletonList(new TeamUserEntry()
                .username(userAName)
                .role(TeamUserEntry.RoleEnum.OWNER)));

        // ---

        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamBName));
    }

    @Test
    public void testTeamMaintainers() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "orgA_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String teamName = "teamA_" + randomString();
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));

        // ---

        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest().username(userAName).type(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userAName));

        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(userAName)
                .role(TeamUserEntry.RoleEnum.MAINTAINER)));

        String userBName = "userB_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userBName)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyB = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userBName));

        // ---

        setApiKey(apiKeyB.getKey());

        try {
            teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                    .username(userBName)
                    .role(TeamUserEntry.RoleEnum.MEMBER)));
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        setApiKey(apiKeyA.getKey());
        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(userBName)
                .role(TeamUserEntry.RoleEnum.MEMBER)));
    }

    @Test
    public void testNewTeamOwner() throws Exception {
        String userA = "userA_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());

        usersApi.createOrUpdateUser(new CreateUserRequest().username(userA).type(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse userAKey = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userA));

        String userB = "userA_" + randomString();

        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userB)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse userBKey = apiKeyResource.createUserApiKey(new CreateApiKeyRequest()
                .username(userB));

        // ---

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "orgA_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.addUsersToTeam(orgName, "default", false, Collections.singletonList(new TeamUserEntry()
                .username(userA)
                .role(TeamUserEntry.RoleEnum.OWNER)));

        // ---

        setApiKey(userBKey.getKey());

        // ---

        String teamName = "teamA_" + randomString();
        try {
            teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        setApiKey(userAKey.getKey());

        // ---

        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));
        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(userB)
                .role(TeamUserEntry.RoleEnum.MEMBER)));
    }

    @Test
    public void testTeamDelete() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "orgA_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String teamName = "teamA_" + randomString();
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));

        // ---

        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username("admin")
                .role(TeamUserEntry.RoleEnum.OWNER)));

        // ---

        List<TeamEntry> l = teamsApi.listTeams(orgName);
        assertEquals(2, l.size());

        // ---

        teamsApi.deleteTeam(orgName, teamName);

        // ---

        l = teamsApi.listTeams(orgName);
        assertEquals(1, l.size());
    }

    @Test
    public void testOrgProjects() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "orgA_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String teamName = "teamA_" + randomString();
        CreateTeamResponse ctr = teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));

        // ---

        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest().username(userAName).type(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userAName));

        String userBName = "userB_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userBName)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyB = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userBName));

        // ---

        setApiKey(apiKeyA.getKey());

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String projectName = "projectA_" + randomString();
        try {
            projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();
        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(userAName)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        setApiKey(apiKeyA.getKey());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));

        // ---

        setApiKey(apiKeyB.getKey());

        try {
            projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                    .name(projectName)
                    .description("new description")
                    .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        setApiKey(apiKeyA.getKey());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .description("new description")
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        setApiKey(apiKeyA.getKey());
        projectsApi.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .teamId(ctr.getId())
                .orgName(orgName)
                .teamName(teamName)
                .level(ResourceAccessEntry.LevelEnum.WRITER));

        // ---

        setApiKey(apiKeyB.getKey());

        try {
            projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                    .name(projectName)
                    .description("another description")
                    .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();
        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(userBName)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        setApiKey(apiKeyB.getKey());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .description("another description")
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
    }

    @Test
    public void testOrgPublicSecrets() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgAName = "orgA_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgAName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String teamAName = "teamA_" + randomString();
        teamsApi.createOrUpdateTeam(orgAName, new TeamEntry().name(teamAName));

        // ---

        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest().username(userAName).type(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userAName));

        String userBName = "userB_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userBName)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse apiKeyB = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userBName));

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
        teamsApi.addUsersToTeam(orgAName, teamAName, false, Collections.singletonList(new TeamUserEntry()
                .username(userAName)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

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

    @Test
    public void testInventory() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String teamName = "teamA_" + randomString();

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));

        // ---

        String inventoryName = "inv_" + randomString();

        InventoriesApi inventoryResource = new InventoriesApi(getApiClient());
        inventoryResource.createOrUpdateInventory(orgName, new InventoryEntry()
                .name(inventoryName)
                .visibility(InventoryEntry.VisibilityEnum.PRIVATE));

        // ---

        inventoryResource.updateInventoryAccessLevel(orgName, inventoryName, new ResourceAccessEntry()
                .orgName(orgName)
                .teamName(teamName)
                .level(ResourceAccessEntry.LevelEnum.READER));

        // ---

        String userAName = "userA_" + randomString();
        String userBName = "userB_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest().username(userAName).type(CreateUserRequest.TypeEnum.LOCAL));
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userBName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        // ---

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakrA = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userAName));
        CreateApiKeyResponse cakrB = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userBName));

        // ---

        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(userAName)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        setApiKey(cakrA.getKey());

        inventoryResource.getInventory(orgName, inventoryName);

        // ---

        setApiKey(cakrB.getKey());

        try {
            inventoryResource.getInventory(orgName, inventoryName);
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();

        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(userBName)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        setApiKey(cakrB.getKey());

        inventoryResource.getInventory(orgName, inventoryName);
    }

    @Test
    public void testTeamUsersUpsert() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String teamName = "team_" + randomString();

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));

        // ---

        String username = "user_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        // ---

        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(username)
                .role(TeamUserEntry.RoleEnum.MEMBER)));
        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(username)
                .role(TeamUserEntry.RoleEnum.MAINTAINER)));
    }

    @Test
    public void testSecretAccessLevels() throws Exception {
        SecretsApi secretResource = new SecretsApi(getApiClient());

        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String secretName = "secret_" + randomString();
        SecretOperationResponse sor = addPlainSecret(orgName, secretName, false, null, new byte[]{0, 1, 2});
        secretResource.updateSecretV1(orgName, secretName, new SecretUpdateRequest()
                .id(sor.getId())
                .visibility(SecretUpdateRequest.VisibilityEnum.PRIVATE));

        // ---

        String username = "user_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(username));

        // ---

        setApiKey(cakr.getKey());

        try {
            new SecretsV2Api(getApiClient()).getSecret(orgName, secretName);
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();

        // ---

        String teamName = "team_" + randomString();

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));

        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(username)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        setApiKey(cakr.getKey());

        try {
            new SecretsV2Api(getApiClient()).getSecret(orgName, secretName);
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();

        secretResource.updateSecretAccessLevel(orgName, secretName, new ResourceAccessEntry()
                .teamName(teamName)
                .level(ResourceAccessEntry.LevelEnum.READER));

        // ---

        setApiKey(cakr.getKey());

        SecretEntryV2 s = new SecretsV2Api(getApiClient()).getSecret(orgName, secretName);
        assertEquals(secretName, s.getName());

        try {
            secretResource.delete(orgName, secretName);
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();

        secretResource.updateSecretAccessLevel(orgName, secretName, new ResourceAccessEntry()
                .teamName(teamName)
                .level(ResourceAccessEntry.LevelEnum.OWNER));

        // ---

        setApiKey(cakr.getKey());

        GenericOperationResult r = secretResource.delete(orgName, secretName);
        assertEquals(GenericOperationResult.ResultEnum.DELETED, r.getResult());
    }

    /**
     * Public organizations must be visible
     * regardless of whether the user is in the org or not.
     */
    @Test
    public void testPublicOrgVisibility() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName)
                .visibility(OrganizationEntry.VisibilityEnum.PUBLIC));

        assertTrue(organizationsApi.listOrgs(true, null, null, null).stream().anyMatch(o -> o.getName().equals(orgName)));

        // ---

        String userName = "user_" + randomString();
        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeysApi.createUserApiKey(new CreateApiKeyRequest()
                .username(userName)
                .userType(CreateApiKeyRequest.UserTypeEnum.LOCAL));

        setApiKey(cakr.getKey());

        assertTrue(organizationsApi.listOrgs(false, null, null, null).stream().anyMatch(o -> o.getName().equals(orgName)));

        // ---

        resetApiKey();
        organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName)
                .visibility(OrganizationEntry.VisibilityEnum.PRIVATE));

        assertTrue(organizationsApi.listOrgs(true, null, null, null).stream().anyMatch(o -> o.getName().equals(orgName)));

        // ---

        setApiKey(cakr.getKey());
        organizationsApi = new OrganizationsApi(getApiClient());
        assertFalse(organizationsApi.listOrgs(true, null, null, null).stream().anyMatch(o -> o.getName().equals(orgName)));
    }

    /**
     * Organization owners should see the organization and all resources
     * regardless of whether they're in the org (team) or not.
     */
    @Test
    public void testOwnersVisibility() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName)
                .visibility(OrganizationEntry.VisibilityEnum.PRIVATE));

        // ---

        String userName = "user_" + randomString();
        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeysApi.createUserApiKey(new CreateApiKeyRequest()
                .username(userName)
                .userType(CreateApiKeyRequest.UserTypeEnum.LOCAL));

        setApiKey(cakr.getKey());

        assertFalse(organizationsApi.listOrgs(true, null, null, null).stream().anyMatch(o -> o.getName().equals(orgName)));

        // ---

        resetApiKey();

        organizationsApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName)
                .owner(new EntityOwner()
                        .username(userName)
                        .userType(EntityOwner.UserTypeEnum.LOCAL)));

        // ---

        setApiKey(cakr.getKey());

        assertTrue(organizationsApi.listOrgs(true, null, null, null).stream().anyMatch(o -> o.getName().equals(orgName)));

        // ---

        resetApiKey();

        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PRIVATE));

        String secretName = "secret_" + randomString();
        addPlainSecret(orgName, secretName, false, null, "hello!".getBytes());

        String jsonStoreName = "store_" + randomString();
        JsonStoreApi jsonStoreApi = new JsonStoreApi(getApiClient());
        jsonStoreApi.createOrUpdateJsonStore(orgName, new JsonStoreRequest()
                .name(jsonStoreName)
                .visibility(JsonStoreRequest.VisibilityEnum.PRIVATE));

        // ---

        setApiKey(cakr.getKey());

        assertTrue(projectsApi.findProjects(orgName, null, null, null).stream().anyMatch(p -> p.getName().equals(projectName)));

        assertTrue(new SecretsV2Api(getApiClient()).listSecrets(orgName, null, null, null).stream().anyMatch(s -> s.getName().equals(secretName)));

        assertTrue(jsonStoreApi.listJsonStores(orgName, null, null, null).stream().anyMatch(p -> p.getName().equals(jsonStoreName)));
    }
}
