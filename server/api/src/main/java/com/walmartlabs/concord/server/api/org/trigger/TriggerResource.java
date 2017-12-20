package com.walmartlabs.concord.server.api.org.trigger;

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

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Api(value = "Triggers", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public interface TriggerResource {

    /**
     * List process trigger definitions for the specified project and repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @GET
    @ApiOperation("List trigger definitions")
    @Path("/{orgName}/trigger/refresh/{projectName}/{repositoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    List<TriggerEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                            @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                            @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName);

    /**
     * Refresh process trigger definitions for all projects.
     *
     * @return
     */
    @POST
    @ApiOperation("Refresh trigger definitions for all projects")
    @Path("/trigger/refresh")
    Response refreshAll();

    /**
     * Refresh process trigger definitions for the specified project and repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @POST
    @ApiOperation("Refresh trigger definitions for the specified project and repository")
    @Path("/{orgName}/trigger/refresh/{projectName}/{repositoryName}")
    Response refresh(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                     @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                     @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName);
}
