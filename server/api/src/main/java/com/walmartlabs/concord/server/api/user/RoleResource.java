package com.walmartlabs.concord.server.api.user;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api("Role")
@Path("/api/v1/role")
public interface RoleResource {

    /**
     * Create a new role or update an existing one.
     *
     * @param entry
     * @return
     */
    @POST
    @ApiOperation("Create or update role")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateRoleResponse createOrUpdate(@ApiParam RoleEntry entry);

    /**
     * List roles.
     *
     * @return
     */
    @GET
    @ApiOperation("List roles")
    @Produces(MediaType.APPLICATION_JSON)
    List<RoleEntry> get();

    /**
     * Delete an existing role.
     *
     * @param name
     * @return
     */
    @DELETE
    @ApiOperation("Delete a role")
    @Path("/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteRoleResponse delete(@ApiParam @PathParam("name") @ConcordKey String name);
}
