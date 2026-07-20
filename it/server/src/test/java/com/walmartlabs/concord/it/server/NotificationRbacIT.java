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
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class NotificationRbacIT extends AbstractServerIT {

    @Test
    public void testAdminCanCreateAndDismissNotifications() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        // Create org and project
        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));

        // Get admin user's API key
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        String adminUsername = "admin_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(adminUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        usersApi.updateUserRoles(adminUsername, new UpdateUserRolesRequest()
                .roles(Collections.singleton("concordAdmin")));
        CreateApiKeyResponse adminApiKey = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(adminUsername));

        // Switch to admin user
        setApiKey(adminApiKey.getKey());

        NotificationsApi notificationsApi = new NotificationsApi(getApiClient());
        ProjectEntry project = projectsApi.getProjectByNameAndOrg(orgName, projectName);

        // Create notification
        NotificationEntry notification = new NotificationEntry()
                .projectId(project.getId())
                .summary("Test Notification")
                .body("Test Body")
                .triggerEmail(false);

        NotificationOperationResponse createResponse = notificationsApi.createNotification(notification);
        assertTrue(createResponse.getOk());
        UUID notificationId = createResponse.getId();
        assertNotNull(notificationId);

        // Dismiss notification
        GenericOperationResult dismissResponse = notificationsApi.dismissNotification(notificationId);
        assertEquals(GenericOperationResult.ResultEnum.DELETED, dismissResponse.getResult());

        // Verify notification is dismissed
        NotificationEntry dismissedNotification = notificationsApi.getNotification(notificationId);
        assertNotNull(dismissedNotification.getDismissedTimestamp());
    }

    @Test
    public void testModeratorCanCreateAndDismissNotifications() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        // Create org and project
        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));

        // Create moderator user
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        String modUsername = "mod_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(modUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        usersApi.updateUserRoles(modUsername, new UpdateUserRolesRequest()
                .roles(Collections.singleton("concordModerator")));
        CreateApiKeyResponse modApiKey = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(modUsername));

        // Switch to moderator user
        setApiKey(modApiKey.getKey());

        NotificationsApi notificationsApi = new NotificationsApi(getApiClient());
        ProjectEntry project = projectsApi.getProjectByNameAndOrg(orgName, projectName);

        // Create notification
        NotificationEntry notification = new NotificationEntry()
                .projectId(project.getId())
                .summary("Moderator Notification")
                .body("Moderator Body")
                .triggerEmail(false);

        NotificationOperationResponse createResponse = notificationsApi.createNotification(notification);
        assertTrue(createResponse.getOk());
        UUID notificationId = createResponse.getId();
        assertNotNull(notificationId);

        // Dismiss notification
        GenericOperationResult dismissResponse = notificationsApi.dismissNotification(notificationId);
        assertEquals(GenericOperationResult.ResultEnum.DELETED, dismissResponse.getResult());

        // Verify notification is dismissed
        NotificationEntry dismissedNotification = notificationsApi.getNotification(notificationId);
        assertNotNull(dismissedNotification.getDismissedTimestamp());
    }

    @Test
    public void testNonAdminNonModeratorCannotCreateNotifications() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        // Create org and project
        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));

        // Create regular user (no special roles)
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        String regularUsername = "regular_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(regularUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse regularApiKey = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(regularUsername));

        // Switch to regular user
        setApiKey(regularApiKey.getKey());

        NotificationsApi notificationsApi = new NotificationsApi(getApiClient());
        ProjectEntry project = projectsApi.getProjectByNameAndOrg(orgName, projectName);

        // Attempt to create notification - should fail
        NotificationEntry notification = new NotificationEntry()
                .projectId(project.getId())
                .summary("Unauthorized Notification")
                .body("Unauthorized Body")
                .triggerEmail(false);

        ApiException exception = assertThrows(ApiException.class, () ->
                notificationsApi.createNotification(notification));
        assertEquals(401, exception.getCode());
    }

    @Test
    public void testNonAdminNonModeratorCannotDismissNotifications() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());

        // Create org and project
        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));

        // Create and create a notification as admin
        String adminUsername = "admin_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(adminUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        usersApi.updateUserRoles(adminUsername, new UpdateUserRolesRequest()
                .roles(Collections.singleton("concordAdmin")));
        CreateApiKeyResponse adminApiKey = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(adminUsername));

        setApiKey(adminApiKey.getKey());

        NotificationsApi notificationsApi = new NotificationsApi(getApiClient());
        ProjectEntry project = projectsApi.getProjectByNameAndOrg(orgName, projectName);

        NotificationEntry notification = new NotificationEntry()
                .projectId(project.getId())
                .summary("Test Notification")
                .body("Test Body")
                .triggerEmail(false);

        NotificationOperationResponse createResponse = notificationsApi.createNotification(notification);
        UUID notificationId = createResponse.getId();

        // Create regular user (no special roles)
        String regularUsername = "regular_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(regularUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        CreateApiKeyResponse regularApiKey = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(regularUsername));

        // Switch to regular user
        setApiKey(regularApiKey.getKey());

        // Attempt to dismiss notification - should fail
        ApiException exception = assertThrows(ApiException.class, () ->
                notificationsApi.dismissNotification(notificationId));
        assertEquals(401, exception.getCode());
    }

    @Test
    public void testAdminImplicitlyHasModeratorPrivileges() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        // Create org and project
        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));

        // Create admin user with both concordAdmin and concordModerator roles
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        String adminUsername = "admin_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(adminUsername)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        usersApi.updateUserRoles(adminUsername, new UpdateUserRolesRequest()
                .roles(Collections.singleton("concordAdmin")));
        CreateApiKeyResponse adminApiKey = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(adminUsername));

        setApiKey(adminApiKey.getKey());

        NotificationsApi notificationsApi = new NotificationsApi(getApiClient());
        ProjectEntry project = projectsApi.getProjectByNameAndOrg(orgName, projectName);

        // Admin should be able to create notifications
        NotificationEntry notification = new NotificationEntry()
                .projectId(project.getId())
                .summary("Admin Notification")
                .body("Admin Body")
                .triggerEmail(false);

        NotificationOperationResponse createResponse = notificationsApi.createNotification(notification);
        assertTrue(createResponse.getOk());
    }
}
