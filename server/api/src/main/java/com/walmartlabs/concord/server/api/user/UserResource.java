package com.walmartlabs.concord.server.api.user;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/api/v1/user")
public interface UserResource {

    /**
     * Creates a new user.
     *
     * @param request user's data
     * @return
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateUserResponse create(@Valid CreateUserRequest request);

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteUserResponse delete(@PathParam("id") String id);

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateUserResponse update(@PathParam("id") String id, UpdateUserRequest request);
}
