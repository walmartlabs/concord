package com.walmartlabs.concord.server.api.security.ldap;

import com.walmartlabs.concord.common.validation.ConcordId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api("LDAP")
@Path("/api/v1/ldap")
public interface LdapResource {

    /**
     * Creates a new LDAP group to permissions mapping or updates an existing one.
     *
     * @param request
     * @return
     */
    @POST
    @ApiOperation("Create or update a LDAP group mapping")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateLdapMappingResponse create(@ApiParam CreateLdapMappingRequest request);

    /**
     * Lists LDAP group mappings.
     *
     * @return
     */
    @GET
    @ApiOperation("List LDAP group mappings")
    @Produces(MediaType.APPLICATION_JSON)
    List<LdapMappingEntry> list();

    /**
     * Deletes an existing LDAP group mapping.
     *
     * @param id
     * @return
     */
    @DELETE
    @ApiParam("Delete a LDAP group mapping")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteLdapMappingResponse delete(@ApiParam @PathParam("id") @ConcordId @NotNull String id);
}
