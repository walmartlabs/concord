package com.walmartlabs.concord.server.api.org.project;

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
import com.walmartlabs.concord.server.api.GenericOperationResultResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Api(value = "Repositories", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public interface RepositoryResource {

    @POST
    @ApiOperation("Creates a new project or updates an existing one")
    @Path("/{orgName}/project/{projectName}/repository")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResultResponse createOrUpdate(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                  @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                  @ApiParam @Valid RepositoryEntry entry);

    /**
     * Refresh a repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @POST
    @ApiOperation("Refresh an existing repository")
    @Path("/{orgName}/project/{projectName}/repository/{repositoryName}/refresh")
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResultResponse refreshRepository(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                     @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                     @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName);
}
