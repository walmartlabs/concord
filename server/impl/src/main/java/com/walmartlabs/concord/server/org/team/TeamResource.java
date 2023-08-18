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
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.User;
import com.walmartlabs.concord.server.user.UserType;
import io.swagger.v3.oas.annotations.Parameter;
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
import java.util.stream.Collectors;

@Named
@Singleton
//@Api(value = "Teams", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
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
//    @ApiOperation("Create or update a team")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public CreateTeamResponse createOrUpdate(@Parameter @PathParam("orgName") @ConcordKey String orgName,
                                             @Parameter @Valid TeamEntry entry) {

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
//    @ApiOperation("Get an existing team")
    @Path("/{orgName}/team/{teamName}")
    @Produces(MediaType.APPLICATION_JSON)
    public TeamEntry get(@Parameter @PathParam("orgName") @ConcordKey String orgName,
                         @Parameter @PathParam("teamName") @ConcordKey String teamName) {
        return assertTeam(orgName, teamName, null, true, false);
    }

    /**
     * Delete an existing team.
     */
    @DELETE
//    @ApiOperation("Delete an existing team")
    @Path("/{orgName}/team/{teamName}")
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult delete(@Parameter @PathParam("orgName") @ConcordKey String orgName,
                                         @Parameter @PathParam("teamName") @ConcordKey String teamName) {

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
//    @ApiOperation(value = "List teams", responseContainer = "list", response = TeamEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    public List<TeamEntry> list(@Parameter @PathParam("orgName") @ConcordKey String orgName) {
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
//    @ApiOperation("List users of a team")
    @Path("/{orgName}/team/{teamName}/users")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TeamUserEntry> listUsers(@Parameter @PathParam("orgName") @ConcordKey String orgName,
                                         @Parameter @PathParam("teamName") @ConcordKey String teamName) {

        TeamEntry t = assertTeam(orgName, teamName, TeamRole.MEMBER, true, false);
        return teamDao.listUsers(t.getId());
    }

    /**
     * List LDAP roles of a team.
     *
     * @param teamName
     * @return
     */
    @GET
//    @ApiOperation("List ldap roles of a team")
    @Path("/{orgName}/team/{teamName}/ldapGroups")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TeamLdapGroupEntry> listLdapGroups(@Parameter @PathParam("orgName") @ConcordKey String orgName,
                                                  @Parameter @PathParam("teamName") @ConcordKey String teamName) {

        TeamEntry t = assertTeam(orgName, teamName, TeamRole.MEMBER, true, false);
        return teamDao.listLdapGroups(t.getId());
    }

    /**
     * Add users to the specified team.
     *
     * @param teamName
     * @param users
     * @return
     */
    @PUT
//    @ApiOperation("Add users to a team")
    @Path("/{orgName}/team/{teamName}/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public AddTeamUsersResponse addUsers(@Parameter @PathParam("orgName") @ConcordKey String orgName,
                                         @Parameter @PathParam("teamName") @ConcordKey String teamName,
                                         @Parameter @QueryParam("replace") @DefaultValue("false") boolean replace,
                                         @Parameter @Valid Collection<TeamUserEntry> users) {

        boolean isEmptyUsers = users == null || users.isEmpty();
        if (isEmptyUsers && !replace) {
            throw new ValidationErrorsException("Empty user list");
        }

        teamManager.addUsers(orgName, teamName, replace, users);
        return new AddTeamUsersResponse();
    }

    /**
     * Add LDAP groups to the specified team.
     *
     * @param teamName
     * @param roles
     * @return
     */
    @PUT
//    @ApiOperation("Add LDAP groups to a team")
    @Path("/{orgName}/team/{teamName}/ldapGroups")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public AddTeamLdapGroupsResponse addLdapGroups(@Parameter @PathParam("orgName") @ConcordKey String orgName,
                                                   @Parameter @PathParam("teamName") @ConcordKey String teamName,
                                                   @Parameter @QueryParam("replace") @DefaultValue("false") boolean replace,
                                                   @Parameter @Valid Collection<TeamLdapGroupEntry> roles) {

        boolean isEmptyRoles = roles == null || roles.isEmpty();
        if (isEmptyRoles && !replace) {
            throw new ValidationErrorsException("Empty LDAP group list");
        }

        teamManager.addLdapGroups(orgName, teamName, replace, roles);
        return new AddTeamLdapGroupsResponse();
    }

    /**
     * Remove users from the specified team.
     *
     * @param teamName
     * @param usernames
     * @return
     */
    @DELETE
//    @ApiOperation("Remove users from a team")
    @Path("/{orgName}/team/{teamName}/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RemoveTeamUsersResponse removeUsers(@Parameter @PathParam("orgName") @ConcordKey String orgName,
                                               @Parameter @PathParam("teamName") @ConcordKey String teamName,
                                               @Parameter Collection<String> usernames) {

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
