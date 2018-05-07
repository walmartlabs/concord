package com.walmartlabs.concord.server.api.org.landing;

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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@Api(value = "Landing page", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public interface LandingPageResource {

    /**
     * Create or update a project's landing page registration.
     *
     * @param entry landing's data
     * @return
     */
    @POST
    @Path("/{orgName}/landing_page")
    @ApiOperation("Create or update a project's landing page registration")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateLandingResponse createOrUpdate(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                         @ApiParam @Valid LandingEntry entry);

    /**
     * Lists landing page registrations.
     *
     * @return
     */
    @GET
    @Path("/{orgName}/landing_page")
    @ApiOperation("List landing page registrations")
    @Produces(MediaType.APPLICATION_JSON)
    List<LandingEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName);

    /**
     * Refresh all landing page definitions for all projects.
     *
     * @return
     */
    @POST
    @ApiOperation("Refresh all landing page definitions for all projects")
    @Path("/landing_page/refresh")
    Response refreshAll();

    /**
     * Refresh landing page definitions for the specified project and repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @POST
    @ApiOperation("Refresh landing page definitions for the specified project and repository")
    @Path("{orgName}/landing_page/refresh/{projectName}/{repositoryName}")
    Response refresh(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                     @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                     @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName);
}
