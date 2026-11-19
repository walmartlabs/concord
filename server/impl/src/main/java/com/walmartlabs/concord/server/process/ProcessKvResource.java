package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.KvManager;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import java.util.UUID;

@Path("/api/v1/process")
@Tag(name = "Process KV store")
public class ProcessKvResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessKvResource.class);

    private static final UUID DEFAULT_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final ProcessQueueManager processQueueManager;
    private final KvManager kvManager;
    private final ProjectAccessManager projectAccessManager;

    @Inject
    public ProcessKvResource(ProcessQueueManager processQueueManager,
                             KvManager kvManager,
                             ProjectAccessManager projectAccessManager) {
        this.processQueueManager = processQueueManager;
        this.kvManager = kvManager;
        this.projectAccessManager = projectAccessManager;
    }

    @DELETE
    @Path("{id}/kv/{key}")
    @Operation(description = "Delete KV", operationId = "deleteKv")
    public void removeKey(@PathParam("id") UUID instanceId,
                          @PathParam("key") String key) {

        UUID projectId = assertProjectId(instanceId, ResourceAccessLevel.WRITER);
        kvManager.remove(projectId, key);
    }

    @PUT
    @Path("{id}/kv/{key}/string")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Put string KV", operationId = "putKvString")
    public void putString(@PathParam("id") UUID instanceId,
                          @PathParam("key") String key,
                          @Parameter(required = true) String value) {

        UUID projectId = assertProjectId(instanceId, ResourceAccessLevel.WRITER);
        kvManager.putString(projectId, key, value);
    }

    @GET
    @Path("{id}/kv/{key}/string")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(description = "Get string KV", operationId = "getKvString")
    public String getString(@PathParam("id") UUID instanceId,
                            @PathParam("key") String key) {

        UUID projectId = assertProjectId(instanceId, ResourceAccessLevel.READER);
        return kvManager.getString(projectId, key);
    }

    @PUT
    @Path("{id}/kv/{key}/long")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Put long KV", operationId = "putKvLong")
    public void putLong(@PathParam("id") UUID instanceId,
                        @PathParam("key") String key,
                        @Parameter(required = true) long value) {

        UUID projectId = assertProjectId(instanceId, ResourceAccessLevel.WRITER);
        kvManager.putLong(projectId, key, value);
    }

    @GET
    @Path("{id}/kv/{key}/long")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get long KV", operationId = "getKvLong")
    public Long getLong(@PathParam("id") UUID instanceId,
                        @PathParam("key") String key) {

        UUID projectId = assertProjectId(instanceId, ResourceAccessLevel.READER);
        return kvManager.getLong(projectId, key);
    }

    @POST
    @Path("{id}/kv/{key}/inc")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Inc long KV", operationId = "incKvLong")
    public long incLong(@PathParam("id") UUID instanceId,
                        @PathParam("key") String key) {

        UUID projectId = assertProjectId(instanceId, ResourceAccessLevel.WRITER);
        return kvManager.inc(projectId, key);
    }

    /**
     * Resolves the project ID of the process and verifies that the current
     * principal is allowed to access the project's KV store.
     * <p>
     * Access is granted to:
     * <ul>
     *     <li>admins;</li>
     *     <li>the process itself (session key issued for this process), which is
     *     how the {@code kv} task calls this API;</li>
     *     <li>users with at least the {@code level} access to the process' project.</li>
     * </ul>
     */
    private UUID assertProjectId(UUID instanceId, ResourceAccessLevel level) {
        ProcessEntry entry = processQueueManager.get(PartialProcessKey.from(instanceId));
        if (entry == null) {
            throw new ConcordApplicationException("Process instance not found: " + instanceId, Status.NOT_FOUND);
        }

        boolean admin = Roles.isAdmin();
        boolean ownSession = isOwnSession(entry);

        UUID projectId = entry.projectId();
        if (projectId == null) {
            // processes without a project have no project-level ACLs to check against,
            // only the process itself (or an admin) can use the shared KV namespace
            if (!admin && !ownSession) {
                throw new UnauthorizedException("Access to the KV store of a process without a project is not allowed: " + instanceId);
            }
            log.warn("assertProjectId ['{}'] -> process has no project, using the default value", instanceId);
            return DEFAULT_PROJECT_ID;
        }

        if (!admin && !ownSession) {
            // orgMembersOnly = true, otherwise PUBLIC projects skip the access level check
            projectAccessManager.assertAccess(entry.orgId(), projectId, null, level, true);
        }

        return projectId;
    }

    private static boolean isOwnSession(ProcessEntry entry) {
        SessionKeyPrincipal sessionKey = SessionKeyPrincipal.getCurrent();
        if (sessionKey == null) {
            return false;
        }

        ProcessKey processKey = new ProcessKey(entry.instanceId(), entry.createdAt());
        return processKey.partOf(sessionKey.getProcessKey());
    }
}
