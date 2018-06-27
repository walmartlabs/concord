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
import javax.ws.rs.core.MediaType;

@Api(value = "Process KV store", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/process")
public interface ProcessKvResource {

    @DELETE
    @ApiOperation("Delete KV")
    @Path("{id}/kv/{key}")
    void removeKey(@PathParam("id") String instanceId,
                   @PathParam("key") String key);

    @PUT
    @ApiOperation("Put string KV")
    @Path("{id}/kv/{key}/string")
    @Consumes(MediaType.APPLICATION_JSON)
    void putString(@PathParam("id") String instanceId,
                   @PathParam("key") String key,
                   @ApiParam(required = true) String value);

    @GET
    @ApiOperation("Get string KV")
    @Path("{id}/kv/{key}/string")
    @Produces(MediaType.APPLICATION_JSON)
    String getString(@PathParam("id") String instanceId,
                     @PathParam("key") String key);

    @PUT
    @ApiOperation("Put long KV")
    @Path("{id}/kv/{key}/long")
    @Consumes(MediaType.APPLICATION_JSON)
    void putLong(@PathParam("id") String instanceId,
                 @PathParam("key") String key,
                 @ApiParam(required = true) long value);

    @GET
    @ApiOperation("Get long KV")
    @Path("{id}/kv/{key}/long")
    @Produces(MediaType.APPLICATION_JSON)
    Long getLong(@PathParam("id") String instanceId,
                 @PathParam("key") String key);

    @POST
    @ApiOperation("Inc long KV")
    @Path("{id}/kv/{key}/inc")
    @Produces(MediaType.APPLICATION_JSON)
    long incLong(@PathParam("id") String instanceId,
                 @PathParam("key") String key);
}
