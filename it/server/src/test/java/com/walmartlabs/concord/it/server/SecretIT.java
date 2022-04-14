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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

public class SecretIT extends AbstractServerIT {

    @Test
    public void testOwnerChange() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName));

        // ---

        String secretName = "secret_" + randomString();
        generateKeyPair(orgName, projectName, secretName, false, null);

        // ---

        String userName = "myUser_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        CreateUserResponse cur = usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));

        SecretsApi secretsApi = new SecretsApi(getApiClient());
        SecretUpdateRequest req = new SecretUpdateRequest();
        req.setOwner(new EntityOwner().setId(cur.getId()));
        secretsApi.update(orgName, secretName, req);

        PublicKeyResponse pkr = secretsApi.getPublicKey(orgName, secretName);

        assertNotNull(pkr);
        assertNotNull(pkr.getPublicKey());

        // ---

        secretsApi.delete(orgName, secretName);
        projectsApi.delete(orgName, projectName);
        orgApi.delete(orgName, "yes");
    }

    @Test
    public void testBulkAccessUpdate() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName));

        // ---

        String secretName = "secret_" + randomString();
        generateKeyPair(orgName, projectName, secretName, false, null);


        SecretsApi secretsApi = new SecretsApi(getApiClient());
        TeamsApi teamsApi = new TeamsApi(getApiClient());

        // ---

        String teamName = "team_" + randomString();
        CreateTeamResponse teamResp = teamsApi.createOrUpdate(orgName, new TeamEntry()
                .setName(teamName));

        // --- Typical one-or-more teams bulk access update

        List<ResourceAccessEntry> teams = new ArrayList<>(1);
        teams.add(new ResourceAccessEntry()
                .setOrgName(orgName)
                .setTeamId(teamResp.getId())
                .setTeamName(teamName)
                .setLevel(ResourceAccessEntry.LevelEnum.OWNER));
        GenericOperationResult addTeamsResult = secretsApi.updateAccessLevel_0(orgName, secretName, teams);
        assertNotNull(addTeamsResult);
        assertTrue(addTeamsResult.isOk());

        List<ResourceAccessEntry> currentTeams = secretsApi.getAccessLevel(orgName, secretName);
        assertNotNull(currentTeams);
        assertEquals(1, currentTeams.size());

        // --- Empty teams list clears all

        GenericOperationResult clearTeamsResult = secretsApi.updateAccessLevel_0(orgName, secretName, Collections.emptyList());
        assertNotNull(clearTeamsResult);
        assertTrue(clearTeamsResult.isOk());

        // --- Null list not allowed, throws error

        try {
            secretsApi.updateAccessLevel_0(orgName, secretName, null);
        } catch (ApiException expected) {
            assertEquals(400, expected.getCode());
            assertTrue(expected.getResponseBody().contains("List of teams is null"));
        } catch (Exception e) {
            fail("Expected ApiException. Got " + e.getClass().toString());
        }

        // ---

        teamsApi.delete(orgName, teamName);
        secretsApi.delete(orgName, secretName);
        projectsApi.delete(orgName, projectName);
        orgApi.delete(orgName, "yes");
    }

    @Test
    public void testSecretUpdate() throws Exception {
        String orgNameInit = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgNameInit));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgNameInit, new ProjectEntry()
                .setName(projectName));

        // ---

        String secretName = "secret_" + randomString();
        generateKeyPair(orgNameInit, projectName, secretName, false, null);

        // ---

        String userName = "myUser_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        CreateUserResponse cur = usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));

        // ---

        String newOrgName = "org_" + randomString();
        UUID newOrgId = orgApi.createOrUpdate(new OrganizationEntry().setName(newOrgName)).getId();
        String newSecretName = "name_" + randomString();

        UpdateSecretRequest request = UpdateSecretRequest.builder()
                .newOrgId(newOrgId)
                .newOwnerId(cur.getId())
                .newVisibility(SecretEntry.VisibilityEnum.PRIVATE)
                .newName(newSecretName)
                .build();

        SecretClient secretClient = new SecretClient(getApiClient());
        secretClient.updateSecret(orgNameInit, secretName, request);

        SecretsApi secretsApi = new SecretsApi(getApiClient());

        PublicKeyResponse pkr = secretsApi.getPublicKey(newOrgName, newSecretName);

        assertNotNull(pkr);
        assertNotNull(pkr.getPublicKey());

        SecretEntry secret = secretsApi.get(newOrgName, newSecretName);

        assertNotNull(secret);
        assertEquals(cur.getId(), secret.getOwner().getId());
        assertNull(secret.getProjectName());
        assertEquals(SecretEntry.VisibilityEnum.PRIVATE, secret.getVisibility());

        // ---

        orgApi.delete(orgNameInit, "yes");
        orgApi.delete(newOrgName, "yes");
    }

    @Test
    public void testSecretPasswordUpdate() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String initPassword = "q1q1Q1Q1";

        String secretName = "secret_" + randomString();
        generateKeyPair(orgName, null, secretName, false, initPassword);

        // ---
        String newPassword = "q2q2Q2Q2";

        UpdateSecretRequest request = UpdateSecretRequest.builder()
                .currentPassword(initPassword)
                .newPassword(newPassword)
                .build();

        // ---

        SecretClient secretsApi = new SecretClient(getApiClient());
        secretsApi.updateSecret(orgName, secretName, request);

        KeyPair kp = secretsApi.getData(orgName, secretName, newPassword, SecretEntry.TypeEnum.KEY_PAIR);

        assertNotNull(kp);
    }

    @Test
    public void testSecretDataUpdate() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String secretName = "secret_" + randomString();
        generateKeyPair(orgName, null, secretName, false, null);

        // ---

        UpdateSecretRequest request = UpdateSecretRequest.builder()
                .usernamePassword(CreateSecretRequest.UsernamePassword.of("test", "q1"))
                .build();

        SecretClient secretClient = new SecretClient(getApiClient());
        secretClient.updateSecret(orgName, secretName, request);

        UsernamePassword up = secretClient.getData(orgName, secretName, null, SecretEntry.TypeEnum.USERNAME_PASSWORD);

        assertNotNull(up);
        assertEquals("test", up.getUsername());
        assertArrayEquals("q1".toCharArray(), up.getPassword());
    }

    @Test
    public void testUpdateNonUniqueName() throws Exception {
        String orgName1 = "org_" + randomString();
        String orgName2 = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName1));
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName2));

        // ---

        String secretName = "secret_" + randomString();
        addUsernamePassword(orgName1, secretName, false, null, "test", "q1");
        addUsernamePassword(orgName2, secretName, false, null, "test", "q1");

        UpdateSecretRequest request = UpdateSecretRequest.builder()
                .newOrgName(orgName2)
                .build();

        SecretClient secretClient = new SecretClient(getApiClient());
        ApiException exception = Assertions.assertThrows(ApiException.class,
                () -> secretClient.updateSecret(orgName1, secretName, request));
        assertThat(exception.getMessage(), containsString("Secret already exists"));
    }
}
