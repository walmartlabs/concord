package com.walmartlabs.concord.server.api.org.team;

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


import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.api.GenericOperationResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;

@Api(value = "Teams", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public interface TeamResource {

    /**
     * Creates or updates a team.
     *
     * @param entry
     * @return
     */
    @POST
    @Path("/{orgName}/team")
    @ApiOperation("Create or update a team")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateTeamResponse createOrUpdate(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                      @ApiParam @Valid TeamEntry entry);

    /**
     * Gets an existing team.
     *
     * @param teamName
     * @return
     */
    @GET
    @ApiOperation("Get an existing team")
    @Path("/{orgName}/team/{teamName}")
    @Produces(MediaType.APPLICATION_JSON)
    TeamEntry get(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                  @ApiParam @PathParam("teamName") @ConcordKey String teamName);

    /**
     * Deletes an existing team.
     */
    @DELETE
    @ApiOperation("Delete an existing team")
    @Path("/{orgName}/team/{teamName}")
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResult delete(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                  @ApiParam @PathParam("teamName") @ConcordKey String teamName);

    /**
     * Lists teams.
     *
     * @return
     */
    @GET
    @Path("/{orgName}/team")
    @ApiOperation(value = "List teams", responseContainer = "list", response = TeamEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    List<TeamEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName);

    /**
     * Lists users of a team.
     *
     * @param teamName
     * @return
     */
    @GET
    @ApiOperation("List users of a team")
    @Path("/{orgName}/team/{teamName}/users")
    @Produces(MediaType.APPLICATION_JSON)
    List<TeamUserEntry> listUsers(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                  @ApiParam @PathParam("teamName") @ConcordKey String teamName);

    /**
     * Adds a list of users to a team.
     *
     * @param teamName
     * @param users
     * @return
     */
    @PUT
    @ApiOperation("Add users to a team")
    @Path("/{orgName}/team/{teamName}/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    AddTeamUsersResponse addUsers(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                  @ApiParam @PathParam("teamName") @ConcordKey String teamName,
                                  @ApiParam @QueryParam("replace") @DefaultValue("false") boolean replace,
                                  @ApiParam Collection<TeamUserEntry> users);

    /**
     * Removes a list of users from a team.
     *
     * @param teamName
     * @param usernames
     * @return
     */
    @DELETE
    @ApiOperation("Remove users from a team")
    @Path("/{orgName}/team/{teamName}/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    RemoveTeamUsersResponse removeUsers(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                        @ApiParam @PathParam("teamName") @ConcordKey String teamName,
                                        @ApiParam Collection<String> usernames);
}
