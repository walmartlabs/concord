package com.walmartlabs.concord.server.api.security.ldap;

import com.walmartlabs.concord.common.validation.ConcordId;
import com.walmartlabs.concord.common.validation.ConcordUsername;
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
    @Path("/mapping")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateLdapMappingResponse createOrUpdate(@ApiParam CreateLdapMappingRequest request);

    /**
     * Lists LDAP group mappings.
     *
     * @return
     */
    @GET
    @ApiOperation("List LDAP group mappings")
    @Path("/mapping")
    @Produces(MediaType.APPLICATION_JSON)
    List<LdapMappingEntry> listMappings();

    /**
     * Deletes an existing LDAP group mapping.
     *
     * @param id
     * @return
     */
    @DELETE
    @ApiOperation("Delete a LDAP group mapping")
    @Path("/mapping/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteLdapMappingResponse deleteMapping(@ApiParam @PathParam("id") @ConcordId @NotNull String id);

    /**
     * Lists user's groups.
     *
     * @param username
     * @return
     */
    @GET
    @ApiOperation("Get user's LDAP groups")
    @Path("/query/{username}/group")
    @Produces(MediaType.APPLICATION_JSON)
    List<String> getLdapGroups(@ApiParam @PathParam("username") @ConcordUsername @NotNull String username);
}
