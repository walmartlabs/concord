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
import com.walmartlabs.concord.client2.CreateSecretRequest;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

public class SecretIT extends AbstractServerIT {

    @Test
    public void testOwnerChange() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));

        // ---

        String secretName = "secret_" + randomString();
        generateKeyPair(orgName, projectName, secretName, false, null);

        // ---

        String userName = "myUser_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        CreateUserResponse cur = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        SecretsApi secretsApi = new SecretsApi(getApiClient());
        SecretUpdateRequest req = new SecretUpdateRequest();
        req.setOwner(new EntityOwner().id(cur.getId()));
        secretsApi.updateSecretV1(orgName, secretName, req);

        PublicKeyResponse pkr = secretsApi.getPublicKey(orgName, secretName);

        assertNotNull(pkr);
        assertNotNull(pkr.getPublicKey());

        // ---

        secretsApi.delete(orgName, secretName);
        projectsApi.deleteProject(orgName, projectName);
        orgApi.deleteOrg(orgName, "yes");
    }

    @Test
    public void testBulkAccessUpdate() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName));

        // ---

        String secretName = "secret_" + randomString();
        generateKeyPair(orgName, projectName, secretName, false, null);


        SecretsApi secretsApi = new SecretsApi(getApiClient());
        TeamsApi teamsApi = new TeamsApi(getApiClient());

        // ---

        String teamName = "team_" + randomString();
        CreateTeamResponse teamResp = teamsApi.createOrUpdateTeam(orgName, new TeamEntry()
                .name(teamName));

        // --- Typical one-or-more teams bulk access update

        List<ResourceAccessEntry> teams = new ArrayList<>(1);
        teams.add(new ResourceAccessEntry()
                .orgName(orgName)
                .teamId(teamResp.getId())
                .teamName(teamName)
                .level(ResourceAccessEntry.LevelEnum.OWNER));
        GenericOperationResult addTeamsResult = secretsApi.updateSecretAccessLevelBulk(orgName, secretName, teams);
        assertNotNull(addTeamsResult);
        assertTrue(addTeamsResult.getOk());

        List<ResourceAccessEntry> currentTeams = secretsApi.getSecretAccessLevel(orgName, secretName);
        assertNotNull(currentTeams);
        assertEquals(1, currentTeams.size());

        // --- Empty teams list clears all

        GenericOperationResult clearTeamsResult = secretsApi.updateSecretAccessLevelBulk(orgName, secretName, Collections.emptyList());
        assertNotNull(clearTeamsResult);
        assertTrue(clearTeamsResult.getOk());

        // --- Null list not allowed, throws error

        try {
            secretsApi.updateSecretAccessLevelBulk(orgName, secretName, null);
        } catch (ApiException expected) {
            assertEquals(400, expected.getCode());
            assertTrue(expected.getResponseBody().contains("List of teams is null"));
        } catch (Exception e) {
            fail("Expected ApiException. Got " + e.getClass().toString());
        }

        // ---

        teamsApi.deleteTeam(orgName, teamName);
        secretsApi.delete(orgName, secretName);
        projectsApi.deleteProject(orgName, projectName);
        orgApi.deleteOrg(orgName, "yes");
    }

    @Test
    public void testSecretUpdate() throws Exception {
        String orgNameInit = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgNameInit));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgNameInit, new ProjectEntry()
                .name(projectName));

        // ---

        String secretName = "secret_" + randomString();
        generateKeyPair(orgNameInit, projectName, secretName, false, null);

        // ---

        String userName = "myUser_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        CreateUserResponse cur = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        // ---

        String newOrgName = "org_" + randomString();
        UUID newOrgId = orgApi.createOrUpdateOrg(new OrganizationEntry().name(newOrgName)).getId();
        String newSecretName = "name_" + randomString();

        com.walmartlabs.concord.client2.UpdateSecretRequest request = com.walmartlabs.concord.client2.UpdateSecretRequest.builder()
                .newOrgId(newOrgId)
                .newOwnerId(cur.getId())
                .newVisibility(SecretEntryV2.VisibilityEnum.PRIVATE)
                .newName(newSecretName)
                .build();

        com.walmartlabs.concord.client2.SecretClient secretClient = new com.walmartlabs.concord.client2.SecretClient(getApiClient());
        secretClient.updateSecret(orgNameInit, secretName, request);

        SecretsApi secretsApi = new SecretsApi(getApiClient());

        PublicKeyResponse pkr = secretsApi.getPublicKey(newOrgName, newSecretName);

        assertNotNull(pkr);
        assertNotNull(pkr.getPublicKey());

        SecretEntryV2 secret = new SecretsV2Api(getApiClient()).getSecret(newOrgName, newSecretName);

        assertNotNull(secret);
        assertEquals(cur.getId(), secret.getOwner().getId());
        assertNull(secret.getProjectName());
        assertEquals(SecretEntryV2.VisibilityEnum.PRIVATE, secret.getVisibility());

        // ---

        orgApi.deleteOrg(orgNameInit, "yes");
        orgApi.deleteOrg(newOrgName, "yes");
    }

    @Test
    public void testSecretPasswordUpdate() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String initPassword = "q1q1Q1Q1";

        String secretName = "secret_" + randomString();
        generateKeyPair(orgName, null, secretName, false, initPassword);

        // ---
        String newPassword = "q2q2Q2Q2";

        com.walmartlabs.concord.client2.UpdateSecretRequest request = com.walmartlabs.concord.client2.UpdateSecretRequest.builder()
                .currentPassword(initPassword)
                .newPassword(newPassword)
                .build();

        // ---

        com.walmartlabs.concord.client2.SecretClient secretsApi = new com.walmartlabs.concord.client2.SecretClient(getApiClient());
        secretsApi.updateSecret(orgName, secretName, request);

        KeyPair kp = secretsApi.getData(orgName, secretName, newPassword, SecretEntryV2.TypeEnum.KEY_PAIR);

        assertNotNull(kp);
    }

    @Test
    public void testSecretDataUpdate() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String secretName = "secret_" + randomString();
        generateKeyPair(orgName, null, secretName, false, null);

        // ---

        com.walmartlabs.concord.client2.UpdateSecretRequest request = com.walmartlabs.concord.client2.UpdateSecretRequest.builder()
                .usernamePassword(CreateSecretRequest.UsernamePassword.of("test", "q1"))
                .build();

        com.walmartlabs.concord.client2.SecretClient secretClient = new com.walmartlabs.concord.client2.SecretClient(getApiClient());
        secretClient.updateSecret(orgName, secretName, request);

        UsernamePassword up = secretClient.getData(orgName, secretName, null, SecretEntryV2.TypeEnum.USERNAME_PASSWORD);

        assertNotNull(up);
        assertEquals("test", up.getUsername());
        assertArrayEquals("q1".toCharArray(), up.getPassword());
    }

    @Test
    public void testUpdateNonUniqueName() throws Exception {
        String orgName1 = "org_" + randomString();
        String orgName2 = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName1));
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName2));

        // ---

        String secretName = "secret_" + randomString();
        addUsernamePassword(orgName1, secretName, false, null, "test", "q1");
        addUsernamePassword(orgName2, secretName, false, null, "test", "q1");

        com.walmartlabs.concord.client2.UpdateSecretRequest request = com.walmartlabs.concord.client2.UpdateSecretRequest.builder()
                .newOrgName(orgName2)
                .build();

        com.walmartlabs.concord.client2.SecretClient secretClient = new com.walmartlabs.concord.client2.SecretClient(getApiClient());
        ApiException exception = Assertions.assertThrows(ApiException.class,
                () -> secretClient.updateSecret(orgName1, secretName, request));
        assertThat(exception.getMessage(), containsString("Secret already exists"));
    }

    @Test
    public void testCreateSecretWithMultipleProjectIds() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName1 = "project_" + randomString();
        String projectName2 = "proejct_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse response1 = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName1));
        ProjectOperationResponse response2 = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName2));

        // ---

        String secretName = "secret_" + randomString();
        SecretOperationResponse secretResponse = generateKeyPairWithProjectIds(orgName, new HashSet<>(Arrays.asList(response1.getId(), response2.getId())), secretName, false, null);
        assertEquals(secretResponse.getResult().toString(), "CREATED");
        SecretsApi secretsApi = new SecretsApi(getApiClient());
        SecretsV2Api secretsV2Api = new SecretsV2Api(getApiClient());
        SecretEntryV2 secretEntry = secretsV2Api.getSecret(orgName, secretName);
        assertTrue(secretEntry.getProjects().stream().map(ProjectEntry::getName).anyMatch(projectName -> projectName.equals(projectName1)));
        assertTrue(secretEntry.getProjects().stream().map(ProjectEntry::getName).anyMatch(projectName -> projectName.equals(projectName2)));


        projectsApi.deleteProject(orgName, projectName1);
        projectsApi.deleteProject(orgName, projectName2);
        secretsApi.delete(orgName, secretName);
        orgApi.deleteOrg(orgName, "yes");
    }

    @Test
    public void testCreateSecretWithMultipleProjectNames() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName1 = "project_" + randomString();
        String projectName2 = "proejct_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse response1 = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName1));
        ProjectOperationResponse response2 = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName2));

        // ---

        String secretName = "secret_" + randomString();
        SecretOperationResponse secretResponse = generateKeyPairWithProjectNames(orgName, new HashSet<>(Arrays.asList(projectName1, projectName2)), secretName, false, null);
        assertEquals(secretResponse.getResult().toString(), "CREATED");

        SecretsApi secretsApi = new SecretsApi(getApiClient());
        SecretsV2Api secretsV2Api = new SecretsV2Api(getApiClient());
        SecretEntryV2 secretEntry = secretsV2Api.getSecret(orgName, secretName);
        assertTrue(secretEntry.getProjects().stream().map(ProjectEntry::getName).anyMatch(projectName -> projectName.equals(projectName1)));
        assertTrue(secretEntry.getProjects().stream().map(ProjectEntry::getName).anyMatch(projectName -> projectName.equals(projectName2)));

        projectsApi.deleteProject(orgName, projectName1);
        projectsApi.deleteProject(orgName, projectName2);
        secretsApi.delete(orgName, secretName);
        orgApi.deleteOrg(orgName, "yes");
    }


    @Test
    public void testUpdateSecretWithMultipleProjectNames() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String secretName = "secret_" + randomString();
        SecretOperationResponse secretResponse = generateKeyPair(orgName, secretName, false, null);
        assertEquals(secretResponse.getResult().toString(), "CREATED");

        String projectName1 = "project_" + randomString();
        String projectName2 = "proejct_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse response1 = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName1));
        ProjectOperationResponse response2 = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName2));

        com.walmartlabs.concord.client2.SecretClient secretClient = new com.walmartlabs.concord.client2.SecretClient(getApiClient());
        com.walmartlabs.concord.client2.UpdateSecretRequest request = UpdateSecretRequest.builder()
                .newProjectIds(new HashSet<>(Arrays.asList(response1.getId(), response2.getId())))
                .build();
        secretClient.updateSecret(orgName, secretName, request);


        SecretsApi secretsApi = new SecretsApi(getApiClient());
        SecretsV2Api secretsV2Api = new SecretsV2Api(getApiClient());
        SecretEntryV2 secretEntry = secretsV2Api.getSecret(orgName, secretName);
        assertTrue(secretEntry.getProjects().stream().map(ProjectEntry::getName).anyMatch(projectName -> projectName.equals(projectName1)));
        assertTrue(secretEntry.getProjects().stream().map(ProjectEntry::getName).anyMatch(projectName -> projectName.equals(projectName2)));

        projectsApi.deleteProject(orgName, projectName1);
        projectsApi.deleteProject(orgName, projectName2);
        secretsApi.delete(orgName, secretName);
        orgApi.deleteOrg(orgName, "yes");
    }
}
