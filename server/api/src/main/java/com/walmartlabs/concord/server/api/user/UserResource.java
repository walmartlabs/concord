package com.walmartlabs.concord.server.api.user;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Api("User")
@Path("/api/v1/user")
public interface UserResource {

    /**
     * Creates a new user or updated an existing one.
     *
     * @param request user's data
     * @return
     */
    @POST
    @ApiOperation("Create a new user or update an existing one")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateUserResponse createOrUpdate(@ApiParam @Valid CreateUserRequest request);

    /**
     * Finds an existing user by username.
     *
     * @param username
     * @return
     */
    @GET
    @ApiOperation("Find an user")
    @Produces(MediaType.APPLICATION_JSON)
    UserEntry findByUsername(@QueryParam("username") @NotNull String username);

    /**
     * Removes an existing user.
     *
     * @param id
     * @return
     */
    @DELETE
    @ApiOperation("Delete an existing user")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteUserResponse delete(@ApiParam @PathParam("id") String id);

    /**
     * Updates an existing user.
     *
     * @param id
     * @param request
     * @return
     */
    @PUT
    @ApiOperation("Update an existing user")
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateUserResponse update(@ApiParam @PathParam("id") String id, UpdateUserRequest request);
}
