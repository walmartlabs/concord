package com.walmartlabs.concord.server.api.org;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api(value = "Organizations", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public interface OrganizationResource {

    @POST
    @ApiOperation("Create or update an organization")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateOrganizationResponse createOrUpdate(@ApiParam @Valid OrganizationEntry entry);

    @GET
    @Path("/{orgName}")
    @ApiOperation("Get an existing organization")
    @Produces(MediaType.APPLICATION_JSON)
    OrganizationEntry get(@ApiParam @PathParam("orgName") @ConcordKey String orgName);

    @GET
    @ApiOperation("List organizations")
    @Produces(MediaType.APPLICATION_JSON)
    List<OrganizationEntry> list();
}
