package com.walmartlabs.concord.server.api.org.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import com.walmartlabs.concord.server.api.org.ResourceAccessEntry;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api(value = "Projects", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public interface ProjectResource {

    @POST
    @ApiOperation("Creates a new project or updates an existing one")
    @Path("/{orgName}/project")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ProjectOperationResponse createOrUpdate(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                            @ApiParam @Valid ProjectEntry entry);

    @GET
    @ApiOperation("Get an existing project")
    @Path("/{orgName}/project/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    ProjectEntry get(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                     @ApiParam @PathParam("projectName") @ConcordKey String projectName);

    @GET
    @ApiOperation("List existing projects")
    @Path("/{orgName}/project")
    @Produces(MediaType.APPLICATION_JSON)
    List<ProjectEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName);

    @GET
    @ApiOperation("Get a project's configuration")
    @Path("/{orgName}/project/{projectName}/cfg{path: (.*)?}")
    @Produces(MediaType.APPLICATION_JSON)
    Object getConfiguration(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                            @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                            @ApiParam @PathParam("path") String path);

    @PUT
    @ApiOperation("Update a project's configuration parameter")
    @Path("/{orgName}/project/{projectName}/cfg{path: (.*)?}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResult updateConfiguration(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                               @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                               @ApiParam @PathParam("path") String path,
                                               @ApiParam Object data);

    @PUT
    @ApiOperation("Update a project's configuration parameter")
    @Path("/{orgName}/project/{projectName}/cfg/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResult updateConfiguration(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                               @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                               @ApiParam Object data);

    @DELETE
    @ApiOperation("Delete a project's configuration parameter")
    @Path("/{orgName}/project/{projectName}/cfg{path: (.*)?}")
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResult deleteConfiguration(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                               @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                               @ApiParam @PathParam("path") String path);

    @DELETE
    @ApiOperation("Delete an existing project")
    @Path("/{orgName}/project/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResult delete(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                  @ApiParam @PathParam("projectName") @ConcordKey String projectName);

    @POST
    @ApiOperation("Updates the access level for the specified project and team")
    @Path("/{orgName}/project/{projectName}/access")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResult updateAccessLevel(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                             @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                             @ApiParam @Valid ResourceAccessEntry entry);

    @POST
    @ApiOperation("Encrypts a string with the project's key")
    @Path("/{orgName}/project/{projectName}/encrypt")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    EncryptValueResponse encrypt(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                 @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                 @ApiParam String value);
}
