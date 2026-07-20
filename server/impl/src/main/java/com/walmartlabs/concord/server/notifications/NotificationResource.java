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
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
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
import java.util.UUID;

import static javax.ws.rs.core.Response.Status;

@Path("/api/v2/notification")
@Tag(name = "Notifications")
public class NotificationResource implements Resource {

    private final NotificationsDao notificationsDao;

    @Inject
    public NotificationResource(NotificationsDao notificationsDao) {
        this.notificationsDao = notificationsDao;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @WithTimer
    @Operation(description = "Create a new notification", operationId = "createNotification")
    public NotificationOperationResponse create(@Valid NotificationEntry entry) {
        assertAdminOrModerator();

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
        // TODO: allow the notification's target user to dismiss their own notifications;
        //       for now only admins and moderators can dismiss any notification.
        assertAdminOrModerator();

        NotificationEntry entry = notificationsDao.get(id);
        if (entry == null) {
            throw new ConcordApplicationException("Notification not found: " + id, Status.NOT_FOUND);
        }

        UserPrincipal currentUser = UserPrincipal.assertCurrent();

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

    private static void assertAdminOrModerator() {
        if (!Roles.isAdminOrModerator()) {
            throw new UnauthorizedException("Only admins or moderators can do that");
        }
    }
}
