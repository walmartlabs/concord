package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.walmartlabs.concord.it.common.ServerClient;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the notification resource.
 *
 * A shared admin and moderator user are created once for the class and torn
 * down after all tests complete. Every test creates its own subjects (users,
 * orgs, projects) and destroys them in a finally block.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NotificationIT extends AbstractServerIT {

    // -------------------------------------------------------------------------
    // Shared users (created once, reused by all tests)
    // -------------------------------------------------------------------------

    private ServerClient setupClient;

    private UUID adminUserId;
    private String adminApiKey;

    private UUID moderatorUserId;
    private String moderatorApiKey;

    @BeforeAll
    public void setUpSharedUsers() throws Exception {
        // _init() (from AbstractServerIT) has not yet run at @BeforeAll time,
        // so we create a dedicated client for setup/teardown here.
        setupClient = new ServerClient(ITConstants.SERVER_URL);
        ApiClient client = setupClient.getClient();

        UsersApi usersApi = new UsersApi(client);
        ApiKeysApi apiKeysApi = new ApiKeysApi(client);

        String adminUsername = "notif-admin-" + randomString();
        adminUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(adminUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        usersApi.updateUserRoles(adminUsername, new UpdateUserRolesRequest()
                .roles(Collections.singleton("concordAdmin")));
        adminApiKey = apiKeysApi.createUserApiKey(
                new CreateApiKeyRequest().username(adminUsername)).getKey();

        String modUsername = "notif-mod-" + randomString();
        moderatorUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(modUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        usersApi.updateUserRoles(modUsername, new UpdateUserRolesRequest()
                .roles(Collections.singleton("concordModerator")));
        moderatorApiKey = apiKeysApi.createUserApiKey(
                new CreateApiKeyRequest().username(modUsername)).getKey();
    }

    @AfterAll
    public void tearDownSharedUsers() throws Exception {
        UsersApi usersApi = new UsersApi(setupClient.getClient());
        if (adminUserId != null) usersApi.deleteUser(adminUserId);
        if (moderatorUserId != null) usersApi.deleteUser(moderatorUserId);
    }

    // =========================================================================
    // Creating notifications for each owner type
    // =========================================================================

    @Test
    public void testCreateUserNotification() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());

        String targetUsername = "target-" + randomString();
        UUID targetUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(targetUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();

        setApiKey(adminApiKey);
        try {
            NotificationsApi notif = new NotificationsApi(getApiClient());
            NotificationOperationResponse resp = notif.createNotification(new NotificationEntry()
                    .userId(targetUserId)
                    .summary("User notification")
                    .body("test body")
                    .actionLink("")
                    .triggerEmail(false));

            assertEquals(NotificationOperationResponse.ResultEnum.CREATED, resp.getResult());

            NotificationEntry created = notif.getNotification(resp.getId());
            assertEquals(targetUserId, created.getUserId());
        } finally {
            resetApiKey();
            usersApi.deleteUser(targetUserId);
        }
    }

    @Test
    public void testCreateProjectNotification() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String orgName = "org-" + randomString();
        String projectName = "proj-" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));
        UUID projectId = projectsApi.getProject(orgName, projectName).getId();

        setApiKey(adminApiKey);
        try {
            NotificationsApi notif = new NotificationsApi(getApiClient());
            NotificationOperationResponse resp = notif.createNotification(new NotificationEntry()
                    .projectId(projectId)
                    .summary("Project notification")
                    .body("test body")
                    .actionLink("")
                    .triggerEmail(false));

            assertEquals(NotificationOperationResponse.ResultEnum.CREATED, resp.getResult());

            NotificationEntry created = notif.getNotification(resp.getId());
            assertEquals(projectId, created.getProjectId());
            assertNull(created.getUserId());
            assertNull(created.getOrgId());
        } finally {
            resetApiKey();
            projectsApi.deleteProject(orgName, projectName);
            orgApi.deleteOrg(orgName, "yes");
        }
    }

    @Test
    public void testCreateOrgNotification() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org-" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        UUID orgId = orgApi.getOrg(orgName).getId();

        setApiKey(adminApiKey);
        try {
            NotificationsApi notif = new NotificationsApi(getApiClient());
            NotificationOperationResponse resp = notif.createNotification(new NotificationEntry()
                    .orgId(orgId)
                    .summary("Org notification")
                    .body("test body")
                    .actionLink("")
                    .triggerEmail(false));

            assertEquals(NotificationOperationResponse.ResultEnum.CREATED, resp.getResult());

            NotificationEntry created = notif.getNotification(resp.getId());
            assertEquals(orgId, created.getOrgId());
            assertNull(created.getUserId());
            assertNull(created.getProjectId());
        } finally {
            resetApiKey();
            orgApi.deleteOrg(orgName, "yes");
        }
    }

    @Test
    public void testModeratorCanCreateNotification() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String orgName = "org-" + randomString();
        String projectName = "proj-" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));
        UUID projectId = projectsApi.getProject(orgName, projectName).getId();

        setApiKey(moderatorApiKey);
        try {
            NotificationsApi notif = new NotificationsApi(getApiClient());
            NotificationOperationResponse resp = notif.createNotification(new NotificationEntry()
                    .projectId(projectId)
                    .summary("Moderator-created notification")
                    .body("test body")
                    .actionLink("")
                    .triggerEmail(false));

            assertEquals(NotificationOperationResponse.ResultEnum.CREATED, resp.getResult());
        } finally {
            resetApiKey();
            projectsApi.deleteProject(orgName, projectName);
            orgApi.deleteOrg(orgName, "yes");
        }
    }

    @Test
    public void testRegularUserCannotCreateNotification() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());

        String orgName = "org-" + randomString();
        String projectName = "proj-" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));
        UUID projectId = projectsApi.getProject(orgName, projectName).getId();

        String regularUsername = "regular-" + randomString();
        UUID regularUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(regularUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        String regularApiKey = apiKeysApi.createUserApiKey(
                new CreateApiKeyRequest().username(regularUsername)).getKey();

        setApiKey(regularApiKey);
        try {
            NotificationsApi notif = new NotificationsApi(getApiClient());
            ApiException ex = assertThrows(ApiException.class, () ->
                    notif.createNotification(new NotificationEntry()
                            .projectId(projectId)
                            .summary("Unauthorized")
                            .body("test body")
                            .actionLink("")
                            .triggerEmail(false)));
            assertEquals(403, ex.getCode());
        } finally {
            resetApiKey();
            usersApi.deleteUser(regularUserId);
            projectsApi.deleteProject(orgName, projectName);
            orgApi.deleteOrg(orgName, "yes");
        }
    }

    // =========================================================================
    // Ownership is mutually exclusive
    // =========================================================================

    @Test
    public void testOwnershipIsMutuallyExclusive() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        UsersApi usersApi = new UsersApi(getApiClient());

        String orgName = "org-" + randomString();
        String projectName = "proj-" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));
        UUID projectId = projectsApi.getProject(orgName, projectName).getId();
        UUID orgId = orgApi.getOrg(orgName).getId();

        String targetUsername = "target-" + randomString();
        UUID targetUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(targetUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();

        setApiKey(adminApiKey);
        try {
            NotificationsApi notif = new NotificationsApi(getApiClient());

            // userId + projectId → 400
            ApiException ex = assertThrows(ApiException.class, () ->
                    notif.createNotification(new NotificationEntry()
                            .userId(targetUserId)
                            .projectId(projectId)
                            .summary("dual owner")
                            .body("test body")
                            .actionLink("")
                            .triggerEmail(false)));
            assertEquals(400, ex.getCode());

            // projectId + orgId → 400
            ex = assertThrows(ApiException.class, () ->
                    notif.createNotification(new NotificationEntry()
                            .projectId(projectId)
                            .orgId(orgId)
                            .summary("dual owner")
                            .body("test body")
                            .actionLink("")
                            .triggerEmail(false)));
            assertEquals(400, ex.getCode());

            // userId + orgId → 400
            ex = assertThrows(ApiException.class, () ->
                    notif.createNotification(new NotificationEntry()
                            .userId(targetUserId)
                            .orgId(orgId)
                            .summary("dual owner")
                            .body("test body")
                            .actionLink("")
                            .triggerEmail(false)));
            assertEquals(400, ex.getCode());
        } finally {
            resetApiKey();
            usersApi.deleteUser(targetUserId);
            projectsApi.deleteProject(orgName, projectName);
            orgApi.deleteOrg(orgName, "yes");
        }
    }

    // =========================================================================
    // Dismiss access control
    // =========================================================================

    @Test
    public void testTargetUserCanDismissOwnNotification() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());

        String targetUsername = "target-" + randomString();
        UUID targetUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(targetUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        String targetApiKey = apiKeysApi.createUserApiKey(
                new CreateApiKeyRequest().username(targetUsername)).getKey();

        setApiKey(adminApiKey);
        NotificationsApi adminNotif = new NotificationsApi(getApiClient());
        UUID notifId = adminNotif.createNotification(new NotificationEntry()
                .userId(targetUserId)
                .summary("Dismissable")
                .body("test body")
                .actionLink("")
                .triggerEmail(false)).getId();

        try {
            setApiKey(targetApiKey);
            GenericOperationResult result = new NotificationsApi(getApiClient()).dismissNotification(notifId);
            assertEquals(GenericOperationResult.ResultEnum.DELETED, result.getResult());

            resetApiKey();
            setApiKey(adminApiKey);
            assertNotNull(new NotificationsApi(getApiClient()).getNotification(notifId).getDismissedTimestamp());
        } finally {
            resetApiKey();
            usersApi.deleteUser(targetUserId);
        }
    }

    @Test
    public void testProjectOwnerCanDismissProjectNotification() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String orgName = "org-" + randomString();
        String projectName = "proj-" + randomString();
        String ownerUsername = "owner-" + randomString();

        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));
        UUID projectId = projectsApi.getProject(orgName, projectName).getId();

        UUID ownerUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(ownerUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        String ownerApiKey = apiKeysApi.createUserApiKey(
                new CreateApiKeyRequest().username(ownerUsername)).getKey();

        String teamName = "team-" + randomString();
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));
        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(
                new TeamUserEntry().username(ownerUsername).role(TeamUserEntry.RoleEnum.OWNER)));
        projectsApi.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .orgName(orgName).teamName(teamName).level(ResourceAccessEntry.LevelEnum.OWNER));

        setApiKey(adminApiKey);
        UUID notifId = new NotificationsApi(getApiClient()).createNotification(new NotificationEntry()
                .projectId(projectId)
                .summary("Project notification")
                .body("test body")
                .actionLink("")
                .triggerEmail(false)).getId();

        try {
            setApiKey(ownerApiKey);
            GenericOperationResult result = new NotificationsApi(getApiClient()).dismissNotification(notifId);
            assertEquals(GenericOperationResult.ResultEnum.DELETED, result.getResult());
        } finally {
            resetApiKey();
            usersApi.deleteUser(ownerUserId);
            projectsApi.deleteProject(orgName, projectName);
            orgApi.deleteOrg(orgName, "yes");
        }
    }

    @Test
    public void testOrgMemberCanDismissOrgNotification() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String orgName = "org-" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        UUID orgId = orgApi.getOrg(orgName).getId();

        String memberUsername = "member-" + randomString();
        UUID memberUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(memberUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        String memberApiKey = apiKeysApi.createUserApiKey(
                new CreateApiKeyRequest().username(memberUsername)).getKey();

        String teamName = "team-" + randomString();
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));
        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(
                new TeamUserEntry().username(memberUsername).role(TeamUserEntry.RoleEnum.MEMBER)));

        setApiKey(adminApiKey);
        UUID notifId = new NotificationsApi(getApiClient()).createNotification(new NotificationEntry()
                .orgId(orgId)
                .summary("Org notification")
                .body("test body")
                .actionLink("")
                .triggerEmail(false)).getId();

        try {
            setApiKey(memberApiKey);
            GenericOperationResult result = new NotificationsApi(getApiClient()).dismissNotification(notifId);
            assertEquals(GenericOperationResult.ResultEnum.DELETED, result.getResult());
        } finally {
            resetApiKey();
            usersApi.deleteUser(memberUserId);
            orgApi.deleteOrg(orgName, "yes");
        }
    }

    @Test
    public void testUnrelatedUserCannotDismissNotification() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());

        String orgName = "org-" + randomString();
        String projectName = "proj-" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));
        UUID projectId = projectsApi.getProject(orgName, projectName).getId();

        String unrelatedUsername = "unrelated-" + randomString();
        UUID unrelatedUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(unrelatedUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        String unrelatedApiKey = apiKeysApi.createUserApiKey(
                new CreateApiKeyRequest().username(unrelatedUsername)).getKey();

        setApiKey(adminApiKey);
        UUID notifId = new NotificationsApi(getApiClient()).createNotification(new NotificationEntry()
                .projectId(projectId)
                .summary("Protected notification")
                .body("test body")
                .actionLink("")
                .triggerEmail(false)).getId();

        try {
            setApiKey(unrelatedApiKey);
            ApiException ex = assertThrows(ApiException.class, () ->
                    new NotificationsApi(getApiClient()).dismissNotification(notifId));
            assertEquals(403, ex.getCode());
        } finally {
            resetApiKey();
            usersApi.deleteUser(unrelatedUserId);
            projectsApi.deleteProject(orgName, projectName);
            orgApi.deleteOrg(orgName, "yes");
        }
    }

    // =========================================================================
    // List access control
    // =========================================================================

    @Test
    public void testAnonymousCannotListNotifications() {
        NotificationsApi anonNotif = new NotificationsApi(getApiClientForKey("invalid-api-key"));
        ApiException ex = assertThrows(ApiException.class, () ->
                anonNotif.listNotifications(null, null, 0, 30));
        assertTrue(ex.getCode() == 401 || ex.getCode() == 403,
                "expected 401 or 403, got " + ex.getCode());
    }

    @Test
    public void testUserCanListOwnNotifications() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());

        String targetUsername = "target-" + randomString();
        UUID targetUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(targetUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        String targetApiKey = apiKeysApi.createUserApiKey(
                new CreateApiKeyRequest().username(targetUsername)).getKey();

        setApiKey(adminApiKey);
        UUID notifId = new NotificationsApi(getApiClient()).createNotification(new NotificationEntry()
                .userId(targetUserId)
                .summary("Personal notification")
                .body("test body")
                .actionLink("")
                .triggerEmail(false)).getId();

        try {
            setApiKey(targetApiKey);
            // No ownerKind/ownerId → defaults to the authenticated user's own notifications
            List<NotificationEntry> results = new NotificationsApi(getApiClient())
                    .listNotifications(null, null, 0, 30);
            assertTrue(results.stream().anyMatch(n -> notifId.equals(n.getId())),
                    "user should see their own notification");
        } finally {
            resetApiKey();
            usersApi.deleteUser(targetUserId);
        }
    }

    @Test
    public void testUserCannotListAnotherUsersNotifications() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());

        String targetUsername = "target-" + randomString();
        String otherUsername = "other-" + randomString();
        UUID targetUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(targetUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        UUID otherUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(otherUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        String otherApiKey = apiKeysApi.createUserApiKey(
                new CreateApiKeyRequest().username(otherUsername)).getKey();

        try {
            setApiKey(otherApiKey);
            ApiException ex = assertThrows(ApiException.class, () ->
                    new NotificationsApi(getApiClient())
                            .listNotifications("USER", targetUserId, 0, 30));
            assertEquals(403, ex.getCode());
        } finally {
            resetApiKey();
            usersApi.deleteUser(targetUserId);
            usersApi.deleteUser(otherUserId);
        }
    }

    @Test
    public void testOrgMemberCanListOrgNotifications() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String orgName = "org-" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        UUID orgId = orgApi.getOrg(orgName).getId();

        String memberUsername = "member-" + randomString();
        UUID memberUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(memberUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        String memberApiKey = apiKeysApi.createUserApiKey(
                new CreateApiKeyRequest().username(memberUsername)).getKey();

        String teamName = "team-" + randomString();
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));
        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(
                new TeamUserEntry().username(memberUsername).role(TeamUserEntry.RoleEnum.MEMBER)));

        setApiKey(adminApiKey);
        UUID notifId = new NotificationsApi(getApiClient()).createNotification(new NotificationEntry()
                .orgId(orgId)
                .summary("Org notification")
                .body("test body")
                .actionLink("")
                .triggerEmail(false)).getId();

        try {
            setApiKey(memberApiKey);
            List<NotificationEntry> results = new NotificationsApi(getApiClient())
                    .listNotifications("ORG", orgId, 0, 30);
            assertTrue(results.stream().anyMatch(n -> notifId.equals(n.getId())),
                    "org member should see org notification");
        } finally {
            resetApiKey();
            usersApi.deleteUser(memberUserId);
            orgApi.deleteOrg(orgName, "yes");
        }
    }

    @Test
    public void testNonOrgMemberCannotListOrgNotifications() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());

        String orgName = "org-" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        UUID orgId = orgApi.getOrg(orgName).getId();

        String outsiderUsername = "outsider-" + randomString();
        UUID outsiderUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(outsiderUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        String outsiderApiKey = apiKeysApi.createUserApiKey(
                new CreateApiKeyRequest().username(outsiderUsername)).getKey();

        try {
            setApiKey(outsiderApiKey);
            ApiException ex = assertThrows(ApiException.class, () ->
                    new NotificationsApi(getApiClient())
                            .listNotifications("ORG", orgId, 0, 30));
            assertEquals(403, ex.getCode());
        } finally {
            resetApiKey();
            usersApi.deleteUser(outsiderUserId);
            orgApi.deleteOrg(orgName, "yes");
        }
    }

    @Test
    public void testProjectOwnerCanListProjectNotifications() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String orgName = "org-" + randomString();
        String projectName = "proj-" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));
        UUID projectId = projectsApi.getProject(orgName, projectName).getId();

        String ownerUsername = "owner-" + randomString();
        UUID ownerUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(ownerUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        String ownerApiKey = apiKeysApi.createUserApiKey(
                new CreateApiKeyRequest().username(ownerUsername)).getKey();

        String teamName = "team-" + randomString();
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));
        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(
                new TeamUserEntry().username(ownerUsername).role(TeamUserEntry.RoleEnum.OWNER)));
        projectsApi.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .orgName(orgName).teamName(teamName).level(ResourceAccessEntry.LevelEnum.OWNER));

        setApiKey(adminApiKey);
        UUID notifId = new NotificationsApi(getApiClient()).createNotification(new NotificationEntry()
                .projectId(projectId)
                .summary("Project notification")
                .body("test body")
                .actionLink("")
                .triggerEmail(false)).getId();

        try {
            setApiKey(ownerApiKey);
            List<NotificationEntry> results = new NotificationsApi(getApiClient())
                    .listNotifications("PROJECT", projectId, 0, 30);
            assertTrue(results.stream().anyMatch(n -> notifId.equals(n.getId())),
                    "project owner should see project notification");
        } finally {
            resetApiKey();
            usersApi.deleteUser(ownerUserId);
            projectsApi.deleteProject(orgName, projectName);
            orgApi.deleteOrg(orgName, "yes");
        }
    }

    @Test
    public void testNonProjectOwnerCannotListProjectNotifications() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        UsersApi usersApi = new UsersApi(getApiClient());
        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());

        String orgName = "org-" + randomString();
        String projectName = "proj-" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));
        UUID projectId = projectsApi.getProject(orgName, projectName).getId();

        String readerUsername = "reader-" + randomString();
        UUID readerUserId = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(readerUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        String readerApiKey = apiKeysApi.createUserApiKey(
                new CreateApiKeyRequest().username(readerUsername)).getKey();

        // Grant only READER access (not OWNER)
        String teamName = "team-" + randomString();
        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));
        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(
                new TeamUserEntry().username(readerUsername).role(TeamUserEntry.RoleEnum.MEMBER)));
        projectsApi.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .orgName(orgName).teamName(teamName).level(ResourceAccessEntry.LevelEnum.READER));

        try {
            setApiKey(readerApiKey);
            ApiException ex = assertThrows(ApiException.class, () ->
                    new NotificationsApi(getApiClient())
                            .listNotifications("PROJECT", projectId, 0, 30));
            assertEquals(403, ex.getCode());
        } finally {
            resetApiKey();
            usersApi.deleteUser(readerUserId);
            projectsApi.deleteProject(orgName, projectName);
            orgApi.deleteOrg(orgName, "yes");
        }
    }
}
