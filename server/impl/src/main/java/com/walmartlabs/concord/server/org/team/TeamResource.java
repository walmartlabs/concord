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

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.User;
import com.walmartlabs.concord.server.user.UserType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/v1/org")
@Tag(name = "Teams")
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
     */
    @POST
    @Path("/{orgName}/team")
    @Operation(description = "Create or update a team", operationId = "createOrUpdateTeam")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public CreateTeamResponse createOrUpdate(@PathParam("orgName") @ConcordKey String orgName,
                                             @Valid TeamEntry entry) {

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
     */
    @GET
    @Path("/{orgName}/team/{teamName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get an existing team", operationId = "getTeam")
    public TeamEntry get(@PathParam("orgName") @ConcordKey String orgName,
                         @PathParam("teamName") @ConcordKey String teamName) {
        return assertTeam(orgName, teamName, null, true, false);
    }

    /**
     * Delete an existing team.
     */
    @DELETE
    @Path("/{orgName}/team/{teamName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Delete an existing team", operationId = "deleteTeam")
    public GenericOperationResult delete(@PathParam("orgName") @ConcordKey String orgName,
                                         @PathParam("teamName") @ConcordKey String teamName) {

        teamManager.delete(orgName, teamName);
        return new GenericOperationResult(OperationResult.DELETED);
    }

    /**
     * List teams.
     */
    @GET
    @Path("/{orgName}/team")
    @Operation(description = "List teams", operationId = "listTeams")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TeamEntry> list(@PathParam("orgName") @ConcordKey String orgName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return teamDao.list(org.getId());
    }

    /**
     * List users of a team.
     */
    @GET
    @Path("/{orgName}/team/{teamName}/users")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List users of a team", operationId = "listUserTeams")
    public List<TeamUserEntry> listUsers(@PathParam("orgName") @ConcordKey String orgName,
                                         @PathParam("teamName") @ConcordKey String teamName) {

        TeamEntry t = assertTeam(orgName, teamName, TeamRole.MEMBER, true, false);
        return teamDao.listUsers(t.getId());
    }

    /**
     * List LDAP roles of a team.
     */
    @GET
    @Path("/{orgName}/team/{teamName}/ldapGroups")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List ldap roles of a team")
    public List<TeamLdapGroupEntry> listLdapGroups(@PathParam("orgName") @ConcordKey String orgName,
                                                   @PathParam("teamName") @ConcordKey String teamName) {

        TeamEntry t = assertTeam(orgName, teamName, TeamRole.MEMBER, true, false);
        return teamDao.listLdapGroups(t.getId());
    }

    /**
     * Add users to the specified team.
     */
    @PUT
    @Path("/{orgName}/team/{teamName}/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Add users to a team", operationId = "addUsersToTeam")
    public AddTeamUsersResponse addUsers(@PathParam("orgName") @ConcordKey String orgName,
                                         @PathParam("teamName") @ConcordKey String teamName,
                                         @QueryParam("replace") @DefaultValue("false") boolean replace,
                                         @Valid Collection<TeamUserEntry> users) {

        boolean isEmptyUsers = users == null || users.isEmpty();
        if (isEmptyUsers && !replace) {
            throw new ValidationErrorsException("Empty user list");
        }

        teamManager.addUsers(orgName, teamName, replace, users);
        return new AddTeamUsersResponse();
    }

    /**
     * Add LDAP groups to the specified team.
     */
    @PUT
    @Path("/{orgName}/team/{teamName}/ldapGroups")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Add LDAP groups to a team")
    public AddTeamLdapGroupsResponse addLdapGroups(@PathParam("orgName") @ConcordKey String orgName,
                                                   @PathParam("teamName") @ConcordKey String teamName,
                                                   @QueryParam("replace") @DefaultValue("false") boolean replace,
                                                   @Valid Collection<TeamLdapGroupEntry> roles) {

        boolean isEmptyRoles = roles == null || roles.isEmpty();
        if (isEmptyRoles && !replace) {
            throw new ValidationErrorsException("Empty LDAP group list");
        }

        teamManager.addLdapGroups(orgName, teamName, replace, roles);
        return new AddTeamLdapGroupsResponse();
    }

    /**
     * Remove users from the specified team.
     */
    @DELETE
    @Path("/{orgName}/team/{teamName}/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Remove users from a team", operationId = "removeUsersFromTeam")
    public RemoveTeamUsersResponse removeUsers(@PathParam("orgName") @ConcordKey String orgName,
                                               @PathParam("teamName") @ConcordKey String teamName,
                                               Collection<String> usernames) {

        if (usernames == null || usernames.isEmpty()) {
            throw new ValidationErrorsException("Empty user list");
        }

        // TODO: add user type into request params
        UserType type = UserPrincipal.assertCurrent().getType();
        // TODO: add user domain into request params
        String domain = null;
        teamManager.removeUsers(orgName, teamName, usernames.stream().map(n -> User.of(n, domain, type)).collect(Collectors.toList()));
        return new RemoveTeamUsersResponse();
    }

    private TeamEntry assertTeam(String orgName, String teamName, TeamRole requiredRole,
                                 boolean orgMembersOnly, boolean teamMembersOnly) {

        OrganizationEntry org = orgManager.assertAccess(orgName, orgMembersOnly);
        return teamManager.assertAccess(org.getId(), teamName, requiredRole, teamMembersOnly);
    }
}
