package com.walmartlabs.concord.server.api.org.process;

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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Api(value = "Processes", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public interface ProjectProcessResource {

    /**
     * Starts a new process instance.
     *
     * @param orgName
     * @param projectName
     * @param repoName
     * @param entryPoint
     * @param activeProfiles
     * @return
     */
    @GET
    @ApiOperation("Start a new process")
    @Path("/{orgName}/project/{projectName}/repo/{repoName}/start/{entryPoint}")
    Response start(@ApiParam @PathParam("orgName") String orgName,
                   @ApiParam @PathParam("projectName") String projectName,
                   @ApiParam @PathParam("repoName") String repoName,
                   @ApiParam @PathParam("entryPoint") String entryPoint,
                   @ApiParam @QueryParam("activeProfiles") String activeProfiles,
                   @Context UriInfo uriInfo);
}
