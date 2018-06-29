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

import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.File;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.zipTo;

@Named
@Singleton
@Api(value = "Process Queue", authorizations = {@Authorization("api_key")})
@Path("/api/v1/process/queue")
public class ProcessQueueResourceImpl implements Resource {

    private final ProcessManager processManager;
    private final LogManager logManager;
    private final ProcessStateManager stateManager;

    @Inject
    public ProcessQueueResourceImpl(ProcessManager processManager,
                                    LogManager logManager,
                                    ProcessStateManager stateManager) {

        this.processManager = processManager;
        this.logManager = logManager;
        this.stateManager = stateManager;
    }

    @POST
    @ApiOperation("Take a payload from the queue")
    @Path("/take")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ProcessEntry take(@ApiParam Map<String, Object> capabilities,
                             @Context HttpHeaders headers) {

        ProcessEntry p = processManager.nextPayload(capabilities);
        if (p == null) {
            return null;
        }

        String userAgent = headers.getHeaderString(HttpHeaders.USER_AGENT);
        if (userAgent == null) {
            userAgent = "unknown";
        }
        logManager.info(p.getInstanceId(), "Acquired by: " + userAgent);

        return p;
    }

    @GET
    @ApiOperation(value = "Download the process state", response = File.class)
    @Path("/state/{instanceId}")
    @Produces("application/zip")
    public Response downloadState(@ApiParam @PathParam("instanceId") UUID instanceId) {
        StreamingOutput out = output -> {
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(output)) {
                stateManager.export(instanceId, zipTo(zip));
            }
        };

        return Response.ok(out, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + instanceId + ".zip\"")
                .build();
    }
}
