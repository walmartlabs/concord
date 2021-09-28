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
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class SecretIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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
}
