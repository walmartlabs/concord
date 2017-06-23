package com.walmartlabs.concord.server.api.project;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api("TemplateAlias")
@Path("/api/v1/template/alias")
public interface TemplateAliasResource {

    @POST
    @ApiOperation("Create or update a template alias")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    TemplateAliasResponse createOrUpdate(@ApiParam @Valid TemplateAliasEntry request);

    @GET
    @ApiOperation("List current template aliases")
    @Produces(MediaType.APPLICATION_JSON)
    List<TemplateAliasEntry> list();

    @DELETE
    @ApiOperation("Delete existing template alias")
    @Path("/{alias}")
    TemplateAliasResponse delete(@PathParam("alias") @ConcordKey String alias);
}
