package com.walmartlabs.concord.server.process;

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

import com.walmartlabs.concord.server.org.project.KvDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Named
@Singleton
@Api(value = "Process KV store", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/process")
public class ProcessKvResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessKvResource.class);

    private static final UUID DEFAULT_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final ProcessQueueManager processQueueManager;
    private final KvDao kvDao;

    @Inject
    public ProcessKvResource(ProcessQueueManager processQueueManager, KvDao kvDao) {
        this.processQueueManager = processQueueManager;
        this.kvDao = kvDao;
    }

    @DELETE
    @ApiOperation("Delete KV")
    @Path("{id}/kv/{key}")
    public void removeKey(@PathParam("id") UUID instanceId,
                          @PathParam("key") String key) {

        UUID projectId = assertProjectId(instanceId);
        kvDao.remove(projectId, key);
    }

    @PUT
    @ApiOperation("Put string KV")
    @Path("{id}/kv/{key}/string")
    @Consumes(MediaType.APPLICATION_JSON)
    public void putString(@PathParam("id") UUID instanceId,
                          @PathParam("key") String key,
                          @ApiParam(required = true) String value) {

        UUID projectId = assertProjectId(instanceId);
        kvDao.putString(projectId, key, value);
    }

    @GET
    @ApiOperation("Get string KV")
    @Path("{id}/kv/{key}/string")
    @Produces(MediaType.TEXT_PLAIN)
    public String getString(@PathParam("id") UUID instanceId,
                            @PathParam("key") String key) {

        UUID projectId = assertProjectId(instanceId);
        return kvDao.getString(projectId, key);
    }

    @PUT
    @ApiOperation("Put long KV")
    @Path("{id}/kv/{key}/long")
    @Consumes(MediaType.APPLICATION_JSON)
    public void putLong(@PathParam("id") UUID instanceId,
                        @PathParam("key") String key,
                        @ApiParam(required = true) long value) {

        UUID projectId = assertProjectId(instanceId);
        kvDao.putLong(projectId, key, value);
    }

    @GET
    @ApiOperation("Get long KV")
    @Path("{id}/kv/{key}/long")
    @Produces(MediaType.APPLICATION_JSON)
    public Long getLong(@PathParam("id") UUID instanceId,
                        @PathParam("key") String key) {

        UUID projectId = assertProjectId(instanceId);
        return kvDao.getLong(projectId, key);
    }

    @POST
    @ApiOperation("Inc long KV")
    @Path("{id}/kv/{key}/inc")
    @Produces(MediaType.APPLICATION_JSON)
    public long incLong(@PathParam("id") UUID instanceId,
                        @PathParam("key") String key) {

        UUID projectId = assertProjectId(instanceId);
        return kvDao.inc(projectId, key);
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
