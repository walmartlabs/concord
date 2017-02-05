package com.walmartlabs.concord.server.api.user;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Api("User")
@Path("/api/v1/user")
public interface UserResource {

    /**
     * Creates a new user.
     *
     * @param request user's data
     * @return
     */
    @POST
    @ApiOperation("Create a new user")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateUserResponse create(@ApiParam @Valid CreateUserRequest request);

    @DELETE
    @ApiOperation("Delete an existing user")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteUserResponse delete(@ApiParam @PathParam("id") String id);

    @PUT
    @ApiOperation("Update an existing user")
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateUserResponse update(@ApiParam @PathParam("id") String id, UpdateUserRequest request);
}
