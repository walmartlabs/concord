package com.walmartlabs.concord.server.api.team;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;

@Api("Teams")
@Path("/api/v1/team")
public interface TeamResource {

    /**
     * Creates or updates a team.
     *
     * @param entry
     * @return
     */
    @POST
    @ApiOperation("Create or update a team")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateTeamResponse createOrUpdate(TeamEntry entry);

    /**
     * Gets an existing team.
     *
     * @param teamName
     * @return
     */
    @GET
    @ApiOperation("Get an existing team")
    @Path("/{teamName}")
    @Produces(MediaType.APPLICATION_JSON)
    TeamEntry get(@ApiParam @PathParam("teamName") @ConcordKey String teamName);

    /**
     * Lists teams.
     *
     * @return
     */
    @GET
    @ApiOperation("List teams")
    @Produces(MediaType.APPLICATION_JSON)
    List<TeamEntry> list();

    /**
     * Lists users of a team.
     *
     * @param teamName
     * @return
     */
    @GET
    @ApiOperation("List users of a team")
    @Path("/{teamName}/users")
    @Produces(MediaType.APPLICATION_JSON)
    List<TeamUserEntry> listUsers(@ApiParam @PathParam("teamName") @ConcordKey String teamName);

    /**
     * Adds a list of users to a team.
     *
     * @param teamName
     * @param usernames
     * @return
     */
    @PUT
    @ApiOperation("Add users to a team")
    @Path("/{teamName}/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    AddTeamUsersResponse addUsers(@ApiParam @PathParam("teamName") @ConcordKey String teamName,
                                  @ApiParam Collection<String> usernames);

    /**
     * Removes a list of users from a team.
     *
     * @param teamName
     * @param usernames
     * @return
     */
    @DELETE
    @ApiOperation("Remove users from a team")
    @Path("/{teamName}/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    RemoveTeamUsersResponse removeUsers(@ApiParam @PathParam("teamName") @ConcordKey String teamName,
                                        @ApiParam Collection<String> usernames);
}
