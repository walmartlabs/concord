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

import com.walmartlabs.concord.client.*;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;
import static org.junit.Assert.fail;

public class AttachmentRbacIT extends AbstractServerIT {

    private static final Logger log = LoggerFactory.getLogger(AttachmentRbacIT.class);

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void test() throws Exception {

        // create a new org

        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // add the users A, B and C

        UsersApi usersApi = new UsersApi(getApiClient());
        String userAName = "userA_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest().
                setUsername(userAName).setType(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));

        String userBName = "userB_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userBName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));

        CreateApiKeyResponse apiKeyB = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userBName));

        String userCName = "userC_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userCName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
        UUID userCUUID = usersApi.findByUsername(userCName).getId();
        CreateApiKeyResponse apiKeyC = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userCName));

        // create the user A's team

        String teamName = "team_" + randomString();

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        CreateTeamResponse ctr = teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));

        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userAName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));

        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userCName)
                .setRole(TeamUserEntry.RoleEnum.OWNER)));

        teamsApi.addUsers(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .setUsername(userBName)
                .setRole(TeamUserEntry.RoleEnum.MEMBER)));

        // switch to the user A and create a new private project

        setApiKey(apiKeyA.getKey());

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setVisibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // grant the team access to the project
        projectsApi.updateAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .setTeamId(ctr.getId())
                .setOrgName(orgName)
                .setTeamName(teamName)
                .setLevel(ResourceAccessEntry.LevelEnum.READER));

        // start a new process using the project as the user A

        byte[] payload = archive(AttachmentRbacIT.class.getResource("ansibleEvent").toURI(),
                ITConstants.DEPENDENCIES_DIR);
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);

        StartProcessResponse spr = start(input);
        ProcessApi processApi = new ProcessApi(getApiClient());
        waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.FINISHED);

        log.info("The initiator shall be able to list attachments");
        List<String> attachments = processApi.listAttachments(spr.getInstanceId());
        Assert.assertNotNull("Attachments shall not be null for initiator", attachments);
        Assert.assertSame("Attachment size shall be 1 for initiator", 1, attachments.size());

        File file = processApi.downloadAttachment(spr.getInstanceId(), attachments.get(0));
        Assert.assertNotNull("File object shall not be null for initiator", file);

        // switch to admin and add the user B

        setApiKey(apiKeyA.getKey());

        ProjectEntry projectEntry = projectsApi.get(orgName, projectName);

        // Update userC as owner
        EntityOwner projectOwner = projectEntry.getOwner();

        EntityOwner entityOwner = new EntityOwner();
        entityOwner.setId(userCUUID)
                .setUsername(projectOwner.getUsername())
                .setUserDomain(projectOwner.getUserDomain())
                .setUserType(projectOwner.getUserType())
                .setDisplayName(projectOwner.getDisplayName());

        projectEntry.setOwner(entityOwner);
        projectsApi.createOrUpdate(orgName, projectEntry);

        log.info("The admin shall be able to list attachments");
        // Admin shall be also able to list the attachments

        resetApiKey();
        attachments = processApi.listAttachments(spr.getInstanceId());
        Assert.assertNotNull("Attachments shall not be null for admin", attachments);
        Assert.assertSame("Attachment size shall be 1 for admin", 1, attachments.size());

        file = processApi.downloadAttachment(spr.getInstanceId(), attachments.get(0));
        Assert.assertNotNull("File object shall not be null for admin", file);

        // switch to the user B (non admin) and try to list and download the attachments

        setApiKey(apiKeyB.getKey());

        // Non-admin who is only a member shall not able to list the attachments
        try {
            processApi.listAttachments(spr.getInstanceId());
            fail("Should fail when listing attachments for non-admin");
        } catch (Exception e) {
            Assert.assertNotNull("Exception shall not be null", e);
            Assert.assertTrue("Exception doesn't match", e.getMessage().contains("doesn't have the necessary access level"));
        }

        // Non-admin who is only a member shall not able to download the attachments
        try {
            processApi.downloadAttachment(spr.getInstanceId(), attachments.get(0));
            fail("Should fail when downloading attachments for non-admin");
        } catch (Exception e) {
            Assert.assertNotNull("Exception shall not be null", e);
            Assert.assertTrue("Exception doesn't match", e.getMessage().contains("doesn't have the necessary access level"));
        }

        // Switch to userC who should be able to list and download the attachments since its
        // set to owner of the project
        setApiKey(apiKeyC.getKey());

        attachments = processApi.listAttachments(spr.getInstanceId());
        Assert.assertNotNull("Attachments shall not be null for non-admin who is a owner", attachments);
        Assert.assertSame("Attachment size shall be 1 for non-admin who is a owner",
                1, attachments.size());

        file = processApi.downloadAttachment(spr.getInstanceId(), attachments.get(0));
        Assert.assertNotNull("File object shall not be null for non-admin who is a owner", file);

    }
}
