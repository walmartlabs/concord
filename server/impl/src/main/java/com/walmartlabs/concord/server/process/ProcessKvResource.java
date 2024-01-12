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

import com.walmartlabs.concord.server.org.project.KvManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/api/v1/process")
@Tag(name = "Process KV store")
public class ProcessKvResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessKvResource.class);

    private static final UUID DEFAULT_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final ProcessQueueManager processQueueManager;
    private final KvManager kvManager;

    @Inject
    public ProcessKvResource(ProcessQueueManager processQueueManager, KvManager kvManager) {
        this.processQueueManager = processQueueManager;
        this.kvManager = kvManager;
    }

    @DELETE
    @Path("{id}/kv/{key}")
    @Operation(description = "Delete KV", operationId = "deleteKv")
    public void removeKey(@PathParam("id") UUID instanceId,
                          @PathParam("key") String key) {

        UUID projectId = assertProjectId(instanceId);
        kvManager.remove(projectId, key);
    }

    @PUT
    @Path("{id}/kv/{key}/string")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Put string KV", operationId = "putKvString")
    public void putString(@PathParam("id") UUID instanceId,
                          @PathParam("key") String key,
                          @Parameter(required = true) String value) {

        UUID projectId = assertProjectId(instanceId);
        kvManager.putString(projectId, key, value);
    }

    @GET
    @Path("{id}/kv/{key}/string")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(description = "Get string KV", operationId = "getKvString")
    public String getString(@PathParam("id") UUID instanceId,
                            @PathParam("key") String key) {

        UUID projectId = assertProjectId(instanceId);
        return kvManager.getString(projectId, key);
    }

    @PUT
    @Path("{id}/kv/{key}/long")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Put long KV", operationId = "putKvLong")
    public void putLong(@PathParam("id") UUID instanceId,
                        @PathParam("key") String key,
                        @Parameter(required = true) long value) {

        UUID projectId = assertProjectId(instanceId);
        kvManager.putLong(projectId, key, value);
    }

    @GET
    @Path("{id}/kv/{key}/long")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get long KV", operationId = "getKvLong")
    public Long getLong(@PathParam("id") UUID instanceId,
                        @PathParam("key") String key) {

        UUID projectId = assertProjectId(instanceId);
        return kvManager.getLong(projectId, key);
    }

    @POST
    @Path("{id}/kv/{key}/inc")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Inc long KV", operationId = "incKvLong")
    public long incLong(@PathParam("id") UUID instanceId,
                        @PathParam("key") String key) {

        UUID projectId = assertProjectId(instanceId);
        return kvManager.inc(projectId, key);
    }

    private UUID assertProjectId(UUID instanceId) {
        UUID projectId = processQueueManager.getProjectId(PartialProcessKey.from(instanceId));
        if (projectId == null) {
            log.warn("assertProjectId ['{}'] -> no project found, using the default value", instanceId);
            projectId = DEFAULT_PROJECT_ID;
        }

        return projectId;
    }
}
