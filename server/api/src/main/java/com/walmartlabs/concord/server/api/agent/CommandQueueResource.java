package com.walmartlabs.concord.server.api.agent;

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


import io.swagger.annotations.*;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Api(value = "CommandQueue", authorizations = {@Authorization("api_key")})
@Path("/api/v1/command/queue")
public interface CommandQueueResource {

    @GET
    @ApiOperation(value = "Take command from queue")
    @Path("/take/{agentId}")
    @Produces(MediaType.APPLICATION_JSON)
    CommandEntry take(@PathParam("agentId") String agentId);
}
