package com.walmartlabs.concord.server.notifications;

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

import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static javax.ws.rs.core.Response.Status;

@Path("/api/v2/notification")
@Tag(name = "Notifications")
public class NotificationResource implements Resource {

    private final NotificationsDao notificationsDao;
    private final ProjectAccessManager projectAccessManager;
    private final OrganizationManager orgManager;

    @Inject
    public NotificationResource(NotificationsDao notificationsDao,
                                 ProjectAccessManager projectAccessManager,
                                 OrganizationManager orgManager) {
        this.notificationsDao = notificationsDao;
        this.projectAccessManager = projectAccessManager;
        this.orgManager = orgManager;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "List notifications", operationId = "listNotifications")
    public List<NotificationEntry> list(@QueryParam("ownerKind") @DefaultValue("USER") NotificationOwnerKind ownerKind,
                                        @QueryParam("ownerId") UUID ownerId,
                                        @QueryParam("offset") @DefaultValue("0") int offset,
                                        @QueryParam("limit") @DefaultValue("30") int limit) {
        if (offset < 0) {
            throw new ValidationErrorsException("'offset' must be a positive number or zero");
        }

        if (limit < 1) {
            throw new ValidationErrorsException("'limit' must be a positive number");
        }

        UserPrincipal currentUser = UserPrincipal.assertCurrent();

        // When listing USER-scoped notifications, default ownerId to the current user
        UUID resolvedOwnerId = (ownerKind == NotificationOwnerKind.USER && ownerId == null)
                ? currentUser.getId()
                : ownerId;

        if (ownerKind != NotificationOwnerKind.USER && resolvedOwnerId == null) {
            throw new ValidationErrorsException("'ownerId' is required when ownerKind is " + ownerKind);
        }

        if (Roles.isAdminOrModerator()) {
            // Admins and moderators can list any notifications, optionally filtered by owner kind/id
            return notificationsDao.list(ownerKind, resolvedOwnerId, offset, limit);
        }

        assertOwnerAccess(ownerKind, resolvedOwnerId);
        return notificationsDao.list(ownerKind, resolvedOwnerId, offset, limit);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @WithTimer
    @Operation(description = "Create a new notification", operationId = "createNotification")
    public NotificationOperationResponse create(@Valid NotificationEntry entry) {
        assertAdminOrModerator();

        int ownerCount = (entry.getUserId() != null ? 1 : 0)
                + (entry.getOrgId() != null ? 1 : 0)
                + (entry.getProjectId() != null ? 1 : 0);
        if (ownerCount > 1) {
            throw new ValidationErrorsException("A notification must have exactly one owner: only one of 'userId', 'orgId', or 'projectId' may be set");
        }

        UUID id = notificationsDao.insert(
                entry.getUserId(),
                entry.getOrgId(),
                entry.getProjectId(),
                entry.getRepoId(),
                entry.getSummary(),
                entry.getBody(),
                entry.getActionLink(),
                entry.isTriggerEmail());

        return new NotificationOperationResponse(id, OperationResult.CREATED);
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Get a notification by ID", operationId = "getNotification")
    public NotificationEntry get(@PathParam("id") UUID id) {
        NotificationEntry entry = notificationsDao.get(id);
        if (entry == null) {
            throw new ConcordApplicationException("Notification not found: " + id, Status.NOT_FOUND);
        }
        return entry;
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Dismiss a notification", operationId = "dismissNotification")
    public GenericOperationResult dismiss(@PathParam("id") UUID id) {
        NotificationEntry entry = notificationsDao.get(id);
        if (entry == null) {
            throw new ConcordApplicationException("Notification not found: " + id, Status.NOT_FOUND);
        }

        UserPrincipal currentUser = UserPrincipal.assertCurrent();

        if (!Roles.isAdminOrModerator()) {
            assertOwnerAccess(entry);
        }

        notificationsDao.update(
                id,
                null,
                null,
                null,
                null,
                OffsetDateTime.now(),
                currentUser.getId());

        return new GenericOperationResult(OperationResult.DELETED);
    }

    /**
     * Asserts that the current user has access to the owner of the given notification entry.
     */
    private void assertOwnerAccess(NotificationEntry entry) {
        NotificationOwnerKind kind = entry.effectiveOwnerKind()
                .orElseThrow(() -> new UnauthorizedException("Only admins or moderators can access this notification"));
        UUID ownerId = entry.effectiveOwnerId()
                .orElseThrow(() -> new UnauthorizedException("Only admins or moderators can access this notification"));
        assertOwnerAccess(kind, ownerId);
    }

    /**
     * Asserts that the current user has access to the specified notification owner.
     * <ul>
     *   <li>USER — current user must be the target user</li>
     *   <li>PROJECT — current user must be a project owner</li>
     *   <li>ORG — current user must be an org member</li>
     * </ul>
     */
    private void assertOwnerAccess(NotificationOwnerKind ownerKind, UUID ownerId) {
        switch (ownerKind) {
            case USER -> {
                UserPrincipal currentUser = UserPrincipal.assertCurrent();
                if (!currentUser.getId().equals(ownerId)) {
                    throw new UnauthorizedException("Only the target user can access their own notifications");
                }
            }
            case PROJECT -> projectAccessManager.assertAccess(ownerId, ResourceAccessLevel.OWNER, true);
            case ORG -> orgManager.assertAccess(ownerId, true);
            default -> throw new UnauthorizedException("Only admins or moderators can access this notification");
        }
    }

    private static void assertAdminOrModerator() {
        if (!Roles.isAdminOrModerator()) {
            throw new UnauthorizedException("Only admins or moderators can do that");
        }
    }
}
