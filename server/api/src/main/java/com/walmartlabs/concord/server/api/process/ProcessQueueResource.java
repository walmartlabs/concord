package com.walmartlabs.concord.server.api.process;

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


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Map;
import java.util.UUID;

@Api(value = "Process Queue", authorizations = {@Authorization("api_key")})
@Path("/api/v1/process/queue")
public interface ProcessQueueResource {

    @POST
    @ApiOperation("Take a payload from the queue")
    @Path("/take")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ProcessEntry take(@ApiParam Map<String, Object> agentCapabilities,
                      @Context HttpHeaders headers);


    @GET
    @ApiOperation(value = "Download the process state", response = File.class)
    @Path("/state/{instanceId}")
    @Produces("application/zip")
    Response downloadState(@ApiParam @PathParam("instanceId") UUID instanceId);
}
