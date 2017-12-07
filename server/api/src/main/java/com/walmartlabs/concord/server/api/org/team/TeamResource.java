package com.walmartlabs.concord.server.api.org.team;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;

@Api(value = "Teams", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
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
     * Lists teams.
     *
     * @return
     */
    @GET
    @Path("/{orgName}/team")
    @ApiOperation("List teams")
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
