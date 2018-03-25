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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "Policy", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public interface PolicyResource {

    @GET
    @ApiOperation("Get an org's policy")
    @Path("/{orgName}/policy")
    @Produces(MediaType.APPLICATION_JSON)
    PolicyEntry get(@ApiParam @PathParam("orgName") @ConcordKey String orgName);

    @GET
    @ApiOperation("Get a project's policy")
    @Path("/{orgName}/project/{projectName}/policy")
    @Produces(MediaType.APPLICATION_JSON)
    PolicyEntry get(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                    @ApiParam @PathParam("projectName") @ConcordKey String projectName);
}
