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
import com.walmartlabs.concord.server.api.org.project.ProjectResource;
import com.walmartlabs.concord.server.api.org.secret.SecretResource;
import com.walmartlabs.concord.server.api.org.team.*;
import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.user.CreateUserRequest;
import com.walmartlabs.concord.server.api.user.UserResource;
import com.walmartlabs.concord.server.api.user.UserType;
import com.walmartlabs.concord.server.org.team.TeamManager;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.ForbiddenException;
import java.util.Collections;

import static org.junit.Assert.fail;

@RunWith(ParallelRunner.class)
public class TeamRbacIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testOrgs() {
        OrganizationResource organizationResource = proxy(OrganizationResource.class);

        String orgAName = "orgA_" + randomString();
        CreateOrganizationResponse orgA = organizationResource.createOrUpdate(new OrganizationEntry(orgAName));

        String orgBName = "orgB_" + randomString();
        CreateOrganizationResponse orgB = organizationResource.createOrUpdate(new OrganizationEntry(orgBName));

        // ---

        TeamResource teamResource = proxy(TeamResource.class);

        String teamAName = "teamA_" + randomString();
        teamResource.createOrUpdate(orgAName, new TeamEntry(teamAName));

        String teamBName = "teamB_" + randomString();
        teamResource.createOrUpdate(orgBName, new TeamEntry(teamBName));

        // ---

        UserResource userResource = proxy(UserResource.class);
        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);

        String userAName = "userA_" + randomString();
        userResource.createOrUpdate(new CreateUserRequest(userAName, UserType.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest(userAName, UserType.LOCAL));

        teamResource.addUsers(orgAName, teamAName, Collections.singleton(new TeamUserEntry(userAName, TeamRole.MEMBER)));

        String userBName = "userB_" + randomString();
        userResource.createOrUpdate(new CreateUserRequest(userBName, UserType.LOCAL));
        CreateApiKeyResponse apiKeyB = apiKeyResource.create(new CreateApiKeyRequest(userBName, UserType.LOCAL));

        teamResource.addUsers(orgBName, teamBName, Collections.singleton(new TeamUserEntry(userBName, TeamRole.MEMBER)));

        // ---

        ProjectResource projectResource = proxy(ProjectResource.class);

        setApiKey(apiKeyA.getKey());

        String projectAName = "projectA_" + randomString();
        projectResource.createOrUpdate(orgAName, new ProjectEntry(projectAName));

        try {
            String projectBName = "projectB_" + randomString();
            projectResource.createOrUpdate(orgBName, new ProjectEntry(projectBName));
            fail("should fail");
        } catch (ForbiddenException e) {
        }

        setApiKey(apiKeyB.getKey());

        String projectBName = "projectB_" + randomString();
        projectResource.createOrUpdate(orgBName, new ProjectEntry(projectBName));
    }

    @Test(timeout = 30000)
    public void testTeamCreators() {
        OrganizationResource organizationResource = proxy(OrganizationResource.class);

        String orgName = "orgA_" + randomString();
        organizationResource.createOrUpdate(new OrganizationEntry(orgName));

        // ---

        TeamResource teamResource = proxy(TeamResource.class);

        String teamAName = "teamA_" + randomString();
        teamResource.createOrUpdate(orgName, new TeamEntry(teamAName));

        // ---

        UserResource userResource = proxy(UserResource.class);
        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);

        String userAName = "userA_" + randomString();
        userResource.createOrUpdate(new CreateUserRequest(userAName, UserType.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest(userAName, UserType.LOCAL));

        // ---

        setApiKey(apiKeyA.getKey());

        try {
            teamResource.createOrUpdate(orgName, new TeamEntry(null, null, null, teamAName, "test"));
            fail("Should fail");
        } catch (ForbiddenException e) {
        }

        // ---

        resetApiKey();

        teamResource.addUsers(orgName, teamAName, Collections.singleton(new TeamUserEntry(userAName, TeamRole.MAINTAINER)));

        // ---

        setApiKey(apiKeyA.getKey());

        teamResource.createOrUpdate(orgName, new TeamEntry(null, null, null, teamAName, "test"));

        // ---

        String teamBName = "teamB_" + randomString();

        try {
            teamResource.createOrUpdate(orgName, new TeamEntry(teamBName));
            fail("Should fail");
        } catch (ForbiddenException e) {
        }

        // ---

        resetApiKey();

        teamResource.addUsers(orgName, teamAName, Collections.singleton(new TeamUserEntry(userAName, TeamRole.OWNER)));

        // ---

        teamResource.createOrUpdate(orgName, new TeamEntry(teamBName));
    }

    @Test(timeout = 30000)
    public void testTeamMaintainers() {
        OrganizationResource organizationResource = proxy(OrganizationResource.class);

        String orgName = "orgA_" + randomString();
        organizationResource.createOrUpdate(new OrganizationEntry(orgName));

        // ---

        TeamResource teamResource = proxy(TeamResource.class);

        String teamName = "teamA_" + randomString();
        teamResource.createOrUpdate(orgName, new TeamEntry(teamName));

        // ---

        UserResource userResource = proxy(UserResource.class);
        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);

        String userAName = "userA_" + randomString();
        userResource.createOrUpdate(new CreateUserRequest(userAName, UserType.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest(userAName));

        teamResource.addUsers(orgName, teamName, Collections.singleton(new TeamUserEntry(userAName, TeamRole.MAINTAINER)));

        String userBName = "userB_" + randomString();
        userResource.createOrUpdate(new CreateUserRequest(userBName, UserType.LOCAL));
        CreateApiKeyResponse apiKeyB = apiKeyResource.create(new CreateApiKeyRequest(userBName));

        // ---

        setApiKey(apiKeyB.getKey());

        try {
            teamResource.addUsers(orgName, teamName, Collections.singleton(new TeamUserEntry(userBName, TeamRole.MEMBER)));
            fail("should fail");
        } catch (ForbiddenException e) {
        }

        // ---

        setApiKey(apiKeyA.getKey());
        teamResource.addUsers(orgName, teamName, Collections.singleton(new TeamUserEntry(userBName, TeamRole.MEMBER)));
    }

    @Test(timeout = 30000)
    public void testNewTeamOwner() {
        String userA = "userA_" + randomString();

        UserResource userResource = proxy(UserResource.class);
        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);

        userResource.createOrUpdate(new CreateUserRequest(userA, UserType.LOCAL));
        CreateApiKeyResponse userAKey = apiKeyResource.create(new CreateApiKeyRequest(userA));

        String userB = "userA_" + randomString();

        userResource.createOrUpdate(new CreateUserRequest(userB, UserType.LOCAL));
        CreateApiKeyResponse userBKey = apiKeyResource.create(new CreateApiKeyRequest(userB));

        // ---

        OrganizationResource organizationResource = proxy(OrganizationResource.class);

        String orgName = "orgA_" + randomString();
        organizationResource.createOrUpdate(new OrganizationEntry(orgName));

        // ---

        TeamResource teamResource = proxy(TeamResource.class);
        teamResource.addUsers(orgName, TeamManager.DEFAULT_TEAM_NAME, Collections.singleton(new TeamUserEntry(userA, TeamRole.OWNER)));

        // ---

        setApiKey(userBKey.getKey());

        // ---

        String teamName = "teamA_" + randomString();
        try {
            teamResource.createOrUpdate(orgName, new TeamEntry(teamName));
            fail("Should fail");
        } catch (ForbiddenException e) {
        }

        // ---

        setApiKey(userAKey.getKey());

        // ---

        teamResource.createOrUpdate(orgName, new TeamEntry(teamName));
        teamResource.addUsers(orgName, teamName, Collections.singleton(new TeamUserEntry(userB, TeamRole.MEMBER)));
    }

    @Test(timeout = 30000)
    public void testOrgProjects() {
        OrganizationResource organizationResource = proxy(OrganizationResource.class);

        String orgName = "orgA_" + randomString();
        organizationResource.createOrUpdate(new OrganizationEntry(orgName));

        // ---

        TeamResource teamResource = proxy(TeamResource.class);

        String teamName = "teamA_" + randomString();
        CreateTeamResponse ctr = teamResource.createOrUpdate(orgName, new TeamEntry(teamName));

        // ---

        UserResource userResource = proxy(UserResource.class);
        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);

        String userAName = "userA_" + randomString();
        userResource.createOrUpdate(new CreateUserRequest(userAName, UserType.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest(userAName));

        String userBName = "userB_" + randomString();
        userResource.createOrUpdate(new CreateUserRequest(userBName, UserType.LOCAL));
        CreateApiKeyResponse apiKeyB = apiKeyResource.create(new CreateApiKeyRequest(userBName));

        // ---

        setApiKey(apiKeyA.getKey());

        ProjectResource projectResource = proxy(ProjectResource.class);

        String projectName = "projectA_" + randomString();
        try {
            projectResource.createOrUpdate(orgName, new ProjectEntry(projectName));
            fail("should fail");
        } catch (ForbiddenException e) {
        }

        // ---

        resetApiKey();
        teamResource.addUsers(orgName, teamName, Collections.singleton(new TeamUserEntry(userAName, TeamRole.MEMBER)));

        // ---

        setApiKey(apiKeyA.getKey());
        projectResource.createOrUpdate(orgName, new ProjectEntry(projectName));

        // ---

        setApiKey(apiKeyB.getKey());

        try {
            projectResource.createOrUpdate(orgName, new ProjectEntry(null, projectName, "new description",
                    null, null, null, null, null, null, true));
            fail("should fail");
        } catch (ForbiddenException e) {
        }

        // ---

        setApiKey(apiKeyA.getKey());
        projectResource.createOrUpdate(orgName, new ProjectEntry(null, projectName, "new description",
                null, null, null, null, null, null, true));

        // ---

        com.walmartlabs.concord.server.api.org.project.ProjectResource orgProjectResource =
                proxy(com.walmartlabs.concord.server.api.org.project.ProjectResource.class);

        setApiKey(apiKeyA.getKey());
        orgProjectResource.updateAccessLevel(orgName, projectName, new ResourceAccessEntry(ctr.getId(), ResourceAccessLevel.WRITER));

        // ---

        setApiKey(apiKeyB.getKey());

        try {
            projectResource.createOrUpdate(orgName, new ProjectEntry(null, projectName, "another description",
                    null, null, null, null, null, null, true));
            fail("should fail");
        } catch (ForbiddenException e) {
        }

        // ---

        resetApiKey();
        teamResource.addUsers(orgName, teamName, Collections.singleton(new TeamUserEntry(userBName, TeamRole.MEMBER)));

        // ---

        setApiKey(apiKeyB.getKey());
        projectResource.createOrUpdate(orgName, new ProjectEntry(null, projectName, "another description",
                null, null, null, null, null, null, true));
    }

    @Test(timeout = 30000)
    public void testOrgPublicSecrets() {
        OrganizationResource organizationResource = proxy(OrganizationResource.class);

        String orgAName = "orgA_" + randomString();
        organizationResource.createOrUpdate(new OrganizationEntry(orgAName));

        // ---

        TeamResource teamResource = proxy(TeamResource.class);

        String teamAName = "teamA_" + randomString();
        teamResource.createOrUpdate(orgAName, new TeamEntry(teamAName));

        // ---

        UserResource userResource = proxy(UserResource.class);
        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);

        String userAName = "userA_" + randomString();
        userResource.createOrUpdate(new CreateUserRequest(userAName, UserType.LOCAL));
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest(userAName));

        String userBName = "userB_" + randomString();
        userResource.createOrUpdate(new CreateUserRequest(userBName, UserType.LOCAL));
        CreateApiKeyResponse apiKeyB = apiKeyResource.create(new CreateApiKeyRequest(userBName));

        // ---

        setApiKey(apiKeyA.getKey());

        String secretAName = "secretA_" + randomString();
        try {
            generateKeyPair(orgAName, secretAName, false, null);
            fail("should fail");
        } catch (ForbiddenException e) {
        }

        // ---

        resetApiKey();
        teamResource.addUsers(orgAName, teamAName, Collections.singleton(new TeamUserEntry(userAName, TeamRole.MEMBER)));

        // ---

        setApiKey(apiKeyA.getKey());
        generateKeyPair(orgAName, secretAName, false, null);

        // ---

        SecretResource secretResource = proxy(SecretResource.class);

        setApiKey(apiKeyB.getKey());
        secretResource.getPublicKey(orgAName, secretAName);

        // ---

        setApiKey(apiKeyB.getKey());

        try {
            secretResource.delete(orgAName, secretAName);
            fail("should fail");
        } catch (ForbiddenException e) {
        }

        // ---

        setApiKey(apiKeyA.getKey());
        secretResource.delete(orgAName, secretAName);
    }
}
