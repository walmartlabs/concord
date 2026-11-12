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

        TestUser userA = createUser("userA");
        addUserToTeam(orgAName, teamAName, userA.username, TeamUserEntry.RoleEnum.MEMBER);

        TestUser userB = createUser("userB");
        addUserToTeam(orgBName, teamBName, userB.username, TeamUserEntry.RoleEnum.MEMBER);

        // ---


        setApiKey(userA.apiKey.getKey());

        String projectAName = "projectA_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgAName, new ProjectEntry().name(projectAName));

        try {
            String projectBName = "projectB_" + randomString();
            projectsApi.createOrUpdateProject(orgBName, new ProjectEntry().name(projectBName));
            fail("should fail");
        } catch (ApiException e) {
        }

        setApiKey(userB.apiKey.getKey());

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

        TestUser userA = createUser("userA");

        // ---

        setApiKey(userA.apiKey.getKey());

        try {
            teamsApi.createOrUpdateTeam(orgName, new TeamEntry()
                    .name(teamAName)
                    .description("test"));
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();

        addUserToTeam(orgName, teamAName, userA.username, TeamUserEntry.RoleEnum.MAINTAINER);

        // ---

        setApiKey(userA.apiKey.getKey());

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

        addUserToTeam(orgName, teamAName, userA.username, TeamUserEntry.RoleEnum.OWNER);

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

        TestUser userA = createUser("userA");
        addUserToTeam(orgName, teamName, userA.username, TeamUserEntry.RoleEnum.MAINTAINER);

        TestUser userB = createUser("userB");

        // ---

        setApiKey(userB.apiKey.getKey());

        try {
            teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                    .username(userB.username)
                    .role(TeamUserEntry.RoleEnum.MEMBER)));
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        setApiKey(userA.apiKey.getKey());
        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(userB.username)
                .role(TeamUserEntry.RoleEnum.MEMBER)));
    }

    @Test
    public void testNewTeamOwner() throws Exception {
        TestUser userA = createUser("userA");
        TestUser userB = createUser("userB");

        // ---

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "orgA_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.addUsersToTeam(orgName, "default", false, Collections.singletonList(new TeamUserEntry()
                .username(userA.username)
                .role(TeamUserEntry.RoleEnum.OWNER)));

        // ---

        setApiKey(userB.apiKey.getKey());

        // ---

        String teamName = "teamA_" + randomString();
        try {
            teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        setApiKey(userA.apiKey.getKey());

        // ---

        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));
        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(userB.username)
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

        TestUser userA = createUser("userA");
        TestUser userB = createUser("userB");

        // ---

        setApiKey(userA.apiKey.getKey());

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
                .username(userA.username)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        setApiKey(userA.apiKey.getKey());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));

        // ---

        setApiKey(userB.apiKey.getKey());

        try {
            projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                    .name(projectName)
                    .description("new description")
                    .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        setApiKey(userA.apiKey.getKey());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .description("new description")
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        setApiKey(userA.apiKey.getKey());
        projectsApi.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .teamId(ctr.getId())
                .orgName(orgName)
                .teamName(teamName)
                .level(ResourceAccessEntry.LevelEnum.WRITER));

        // ---

        setApiKey(userB.apiKey.getKey());

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
                .username(userB.username)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        setApiKey(userB.apiKey.getKey());
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

        TestUser userA = createUser("userA");
        TestUser userB = createUser("userB");

        // ---

        setApiKey(userA.apiKey.getKey());

        String secretAName = "secretA_" + randomString();
        try {
            generateKeyPair(orgAName, secretAName, false, null);
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();
        teamsApi.addUsersToTeam(orgAName, teamAName, false, Collections.singletonList(new TeamUserEntry()
                .username(userA.username)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        // ---

        setApiKey(userA.apiKey.getKey());
        generateKeyPair(orgAName, secretAName, false, null);

        // ---

        SecretsApi secretResource = new SecretsApi(getApiClient());

        setApiKey(userB.apiKey.getKey());
        secretResource.getPublicKey(orgAName, secretAName);

        // ---

        setApiKey(userB.apiKey.getKey());

        try {
            secretResource.delete(orgAName, secretAName);
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        setApiKey(userA.apiKey.getKey());
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

        TestUser userA = createUser("userA");
        TestUser userB = createUser("userB");

        // ---

        addUserToTeam(orgName, teamName, userA.username, TeamUserEntry.RoleEnum.MEMBER);

        // ---

        setApiKey(userA.apiKey.getKey());

        inventoryResource.getInventory(orgName, inventoryName);

        // ---

        setApiKey(userB.apiKey.getKey());

        try {
            inventoryResource.getInventory(orgName, inventoryName);
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();

        addUserToTeam(orgName, teamName, userB.username, TeamUserEntry.RoleEnum.MEMBER);

        // ---

        setApiKey(userB.apiKey.getKey());

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

        TestUser user = createUser("user");

        // ---

        setApiKey(user.apiKey.getKey());

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

        addUserToTeam(orgName, teamName, user.username, TeamUserEntry.RoleEnum.MEMBER);

        // ---

        setApiKey(user.apiKey.getKey());

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

        setApiKey(user.apiKey.getKey());

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

        setApiKey(user.apiKey.getKey());

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

        TestUser user = createUser("user");

        setApiKey(user.apiKey.getKey());

        assertTrue(organizationsApi.listOrgs(false, null, null, null).stream().anyMatch(o -> o.getName().equals(orgName)));

        // ---

        resetApiKey();
        organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName)
                .visibility(OrganizationEntry.VisibilityEnum.PRIVATE));

        assertTrue(organizationsApi.listOrgs(true, null, null, null).stream().anyMatch(o -> o.getName().equals(orgName)));

        // ---

        setApiKey(user.apiKey.getKey());
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

        TestUser user = createUser("user");

        setApiKey(user.apiKey.getKey());

        assertFalse(organizationsApi.listOrgs(true, null, null, null).stream().anyMatch(o -> o.getName().equals(orgName)));

        // ---

        resetApiKey();

        organizationsApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName)
                .owner(new EntityOwner()
                        .username(user.username)
                        .userType(EntityOwner.UserTypeEnum.LOCAL)));

        // ---

        setApiKey(user.apiKey.getKey());

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

        setApiKey(user.apiKey.getKey());

        assertTrue(projectsApi.findProjects(orgName, null, null, null).stream().anyMatch(p -> p.getName().equals(projectName)));

        assertTrue(new SecretsV2Api(getApiClient()).listSecrets(orgName, null, null, null).stream().anyMatch(s -> s.getName().equals(secretName)));

        assertTrue(jsonStoreApi.listJsonStores(orgName, null, null, null).stream().anyMatch(p -> p.getName().equals(jsonStoreName)));
    }
}
