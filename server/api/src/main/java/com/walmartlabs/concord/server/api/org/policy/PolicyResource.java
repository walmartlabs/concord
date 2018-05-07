package com.walmartlabs.concord.server.api.org.policy;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.api.GenericOperationResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api(value = "Policy", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v2/policy")
public interface PolicyResource {

    @GET
    @ApiOperation("Get an existing policy")
    @Path("/{policyName}")
    @Produces(MediaType.APPLICATION_JSON)
    PolicyEntry get(@ApiParam @PathParam("policyName") @ConcordKey String policyName);

    @POST
    @ApiOperation("Create or update a policy")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    PolicyOperationResponse createOrUpdate(@ApiParam @Valid PolicyEntry entry);


    @DELETE
    @ApiOperation("Delete an existing policy")
    @Path("/{policyName}")
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResult delete(@ApiParam @PathParam("policyName") @ConcordKey String policyName);

    @PUT
    @ApiOperation("Link an existing policy to an organization or a project")
    @Path("/{policyName}/link")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResult link(@ApiParam @PathParam("policyName") @ConcordKey String policyName,
                                @ApiParam @Valid PolicyLinkEntry entry);

    @DELETE
    @ApiOperation("Unlink an existing policy")
    @Path("/{policyName}/link")
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResult unlink(@ApiParam @PathParam("policyName") @ConcordKey String policyName,
                                  @ApiParam @QueryParam("orgName") @ConcordKey String orgName,
                                  @ApiParam @QueryParam("projectName") @ConcordKey String projectName);

    @GET
    @ApiOperation("List policies, optionally filtering by organization and/or project links")
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    List<PolicyEntry> list(@ApiParam @QueryParam("orgName") @ConcordKey String orgName,
                           @ApiParam @QueryParam("projectName") @ConcordKey String projectName);
}
