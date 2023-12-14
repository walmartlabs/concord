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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;
import static org.junit.jupiter.api.Assertions.*;

public class AttachmentRbacIT extends AbstractServerIT {

    private static final Logger log = LoggerFactory.getLogger(AttachmentRbacIT.class);

    @Test
    public void test() throws Exception {

        // create a new org

        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // add the users A, B and C

        UsersApi usersApi = new UsersApi(getApiClient());
        String userAName = "userA_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest().
                username(userAName).type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKeyA = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userAName));

        String userBName = "userB_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userBName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        CreateApiKeyResponse apiKeyB = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userBName));

        String userCName = "userC_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userCName)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        UUID userCUUID = usersApi.findByUsername(userCName).getId();
        CreateApiKeyResponse apiKeyC = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userCName));

        // create the user A's team

        String teamName = "team_" + randomString();

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        CreateTeamResponse ctr = teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));

        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(userAName)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(userCName)
                .role(TeamUserEntry.RoleEnum.OWNER)));

        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .username(userBName)
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        // switch to the user A and create a new private project

        setApiKey(apiKeyA.getKey());

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // grant the team access to the project
        projectsApi.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .teamId(ctr.getId())
                .orgName(orgName)
                .teamName(teamName)
                .level(ResourceAccessEntry.LevelEnum.READER));

        // start a new process using the project as the user A

        byte[] payload = archive(AttachmentRbacIT.class.getResource("ansibleEvent").toURI(),
                ITConstants.DEPENDENCIES_DIR);
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse spr = start(input);
        ProcessApi processApi = new ProcessApi(getApiClient());
        waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FINISHED);

        log.info("The initiator shall be able to list attachments");
        List<String> attachments = processApi.listAttachments(spr.getInstanceId());
        assertNotNull(attachments, "Attachments shall not be null for initiator");
        assertSame(2, attachments.size(), "Attachment size shall be 2 for initiator");

        try (InputStream is = processApi.downloadAttachment(spr.getInstanceId(), attachments.get(0))) {
            assertNotNull(is, "File object shall not be null for initiator");
        }

        // switch to admin and add the user B

        setApiKey(apiKeyA.getKey());

        ProjectEntry projectEntry = projectsApi.getProject(orgName, projectName);

        // Update userC as owner
        EntityOwner projectOwner = projectEntry.getOwner();

        EntityOwner entityOwner = new EntityOwner()
                .id(userCUUID)
                .username(projectOwner.getUsername())
                .userDomain(projectOwner.getUserDomain())
                .userType(projectOwner.getUserType())
                .displayName(projectOwner.getDisplayName());

        projectEntry.setOwner(entityOwner);
        projectsApi.createOrUpdateProject(orgName, projectEntry);

        log.info("The admin shall be able to list attachments");
        // Admin shall be also able to list the attachments

        resetApiKey();
        attachments = processApi.listAttachments(spr.getInstanceId());
        assertNotNull(attachments, "Attachments shall not be null for admin");
        assertSame(2, attachments.size(), "Attachment size shall be 2 for admin");

        try (InputStream is = processApi.downloadAttachment(spr.getInstanceId(), attachments.get(0))) {
            assertNotNull(is, "File object shall not be null for admin");
        }

        // switch to the user B (non admin) and try to list and download the attachments

        setApiKey(apiKeyB.getKey());

        // Non-admin who is only a member shall not able to list the attachments
        try {
            processApi.listAttachments(spr.getInstanceId());
            fail("Should fail when listing attachments for non-admin");
        } catch (Exception e) {
            assertNotNull(e, "Exception shall not be null");
            assertTrue(e.getMessage().contains("doesn't have the necessary access level"), "Exception doesn't match");
        }

        // Non-admin who is only a member shall not able to download the attachments
        try (InputStream is = processApi.downloadAttachment(spr.getInstanceId(), attachments.get(0))) {
            fail("Should fail when downloading attachments for non-admin");
        } catch (Exception e) {
            assertNotNull(e, "Exception shall not be null");
            assertTrue(e.getMessage().contains("doesn't have the necessary access level"), "Exception doesn't match");
        }

        // Switch to userC who should be able to list and download the attachments since its
        // set to owner of the project
        setApiKey(apiKeyC.getKey());

        attachments = processApi.listAttachments(spr.getInstanceId());
        assertNotNull(attachments, "Attachments shall not be null for non-admin who is a owner");
        assertSame(2, attachments.size(),
                "Attachment size shall be 2 for non-admin who is a owner");

        try (InputStream is = processApi.downloadAttachment(spr.getInstanceId(), attachments.get(0))) {
            assertNotNull(is, "File object shall not be null for non-admin who is a owner");
        }
    }
}
