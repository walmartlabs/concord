package com.walmartlabs.concord.server.org.team;

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

import com.google.inject.Singleton;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Named
@Singleton
@Api(value = "Teams", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public class TeamResource implements Resource {

    private final TeamDao teamDao;
    private final TeamManager teamManager;
    private final OrganizationManager orgManager;

    @Inject
    public TeamResource(TeamDao teamDao,
                        TeamManager teamManager,
                        OrganizationManager orgManager) {

        this.teamDao = teamDao;
        this.teamManager = teamManager;
        this.orgManager = orgManager;
    }

    /**
     * Create or update a team.
     *
     * @param entry
     * @return
     */
    @POST
    @Path("/{orgName}/team")
    @ApiOperation("Create or update a team")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public CreateTeamResponse createOrUpdate(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                             @ApiParam @Valid TeamEntry entry) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        UUID teamId = entry.getId();
        if (teamId == null) {
            teamId = teamDao.getId(org.getId(), entry.getName());
        }
        if (teamId != null) {
            teamManager.update(teamId, entry.getName(), entry.getDescription());
            return new CreateTeamResponse(OperationResult.UPDATED, teamId);
        } else {
            teamId = teamManager.insert(org.getId(), entry.getName(), entry.getDescription());
            return new CreateTeamResponse(OperationResult.CREATED, teamId);
        }
    }

    /**
     * Get an existing team.
     *
     * @param teamName
     * @return
     */
    @GET
    @ApiOperation("Get an existing team")
    @Path("/{orgName}/team/{teamName}")
    @Produces(MediaType.APPLICATION_JSON)
    public TeamEntry get(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                         @ApiParam @PathParam("teamName") @ConcordKey String teamName) {
        return assertTeam(orgName, teamName, null, true, false);
    }

    /**
     * Delete an existing team.
     */
    @DELETE
    @ApiOperation("Delete an existing team")
    @Path("/{orgName}/team/{teamName}")
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult delete(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                         @ApiParam @PathParam("teamName") @ConcordKey String teamName) {

        teamManager.delete(orgName, teamName);
        return new GenericOperationResult(OperationResult.DELETED);
    }

    /**
     * List teams.
     *
     * @return
     */
    @GET
    @Path("/{orgName}/team")
    @ApiOperation(value = "List teams", responseContainer = "list", response = TeamEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    public List<TeamEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return teamDao.list(org.getId());
    }

    /**
     * List users of a team.
     *
     * @param teamName
     * @return
     */
    @GET
    @ApiOperation("List users of a team")
    @Path("/{orgName}/team/{teamName}/users")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TeamUserEntry> listUsers(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                         @ApiParam @PathParam("teamName") @ConcordKey String teamName) {

        TeamEntry t = assertTeam(orgName, teamName, TeamRole.MEMBER, true, false);
        return teamDao.listUsers(t.getId());
    }

    /**
     * Add users to the specified team.
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
    public AddTeamUsersResponse addUsers(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                         @ApiParam @PathParam("teamName") @ConcordKey String teamName,
                                         @ApiParam @QueryParam("replace") @DefaultValue("false") boolean replace,
                                         @ApiParam @Valid Collection<TeamUserEntry> users) {

        if (users == null || users.isEmpty()) {
            throw new ValidationErrorsException("Empty user list");
        }

        teamManager.addUsers(orgName, teamName, replace, users);
        return new AddTeamUsersResponse();
    }

    /**
     * Remove users from the specified team.
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
    public RemoveTeamUsersResponse removeUsers(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                               @ApiParam @PathParam("teamName") @ConcordKey String teamName,
                                               @ApiParam Collection<String> usernames) {

        if (usernames == null || usernames.isEmpty()) {
            throw new ValidationErrorsException("Empty user list");
        }

        teamManager.removeUsers(orgName, teamName, usernames);
        return new RemoveTeamUsersResponse();
    }

    private TeamEntry assertTeam(String orgName, String teamName, TeamRole requiredRole,
                                 boolean orgMembersOnly, boolean teamMembersOnly) {

        OrganizationEntry org = orgManager.assertAccess(orgName, orgMembersOnly);
        return teamManager.assertAccess(org.getId(), teamName, requiredRole, teamMembersOnly);
    }
}
