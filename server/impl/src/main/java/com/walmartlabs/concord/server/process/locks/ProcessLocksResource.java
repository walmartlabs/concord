package com.walmartlabs.concord.server.process.locks;

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

import com.walmartlabs.concord.server.jooq.enums.ProcessLockScope;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Path("/api/v1/process")
@Tag(name = "Process Locks")
public class ProcessLocksResource implements Resource {

    private final ProcessQueueManager processQueueManager;
    private final ProcessLocksDao dao;

    @Inject
    public ProcessLocksResource(ProcessQueueManager processQueueManager,
                                ProcessLocksDao dao) {

        this.processQueueManager = processQueueManager;
        this.dao = dao;
    }

    /**
     * Acquires the lock if it is available and returns the LockResult.acquired = true.
     * If the lock is not available then this method will return the LockResult.acquired = false.
     */
    @POST
    @Path("/{processInstanceId}/lock/{lockName}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Try lock")
    public LockResult tryLock(@PathParam("processInstanceId") UUID instanceId,
                              @PathParam("lockName") String lockName,
                              @QueryParam("scope") @DefaultValue("PROJECT") ProcessLockScope scope) {

        ProcessEntry e = assertProcess(instanceId);

        LockEntry lock = dao.tryLock(new ProcessKey(e.instanceId(), e.createdAt()), e.orgId(), e.projectId(), scope, lockName);
        boolean acquired = lock.instanceId().equals(instanceId);
        return LockResult.builder()
                .acquired(acquired)
                .info(lock)
                .build();
    }

    /**
     * Releases the lock.
     */
    @POST
    @Path("/{processInstanceId}/unlock/{lockName}")
    @WithTimer
    @Operation(description = "Releases the lock")
    public void unlock(@PathParam("processInstanceId") UUID instanceId,
                       @PathParam("lockName") String lockName,
                       @QueryParam("scope") @DefaultValue("PROJECT") ProcessLockScope scope) {

        ProcessEntry e = assertProcess(instanceId);
        dao.delete(e.instanceId(), e.orgId(), e.projectId(), scope, lockName);
    }

    private ProcessEntry assertProcess(UUID instanceId) {
        PartialProcessKey processKey = PartialProcessKey.from(instanceId);
        ProcessEntry p = processQueueManager.get(processKey);

        if (p == null) {
            throw new ConcordApplicationException("Process not found: " + instanceId, Response.Status.NOT_FOUND);
        }

        if (p.orgId() == null) {
            throw new ConcordApplicationException("Organization is required", Response.Status.BAD_REQUEST);
        }

        if (p.projectId() == null) {
            throw new ConcordApplicationException("Project is required", Response.Status.BAD_REQUEST);
        }

        return p;
    }
}
