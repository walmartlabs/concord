package com.walmartlabs.concord.server.api.template;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.List;

@Api("Template")
@Path("/api/v1/template")
public interface TemplateResource {

    @POST
    @ApiOperation("Upload a new template")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    CreateTemplateResponse create(@ApiParam @QueryParam("name") @NotNull @ConcordKey String name,
                                  @ApiParam InputStream data);

    @GET
    @ApiOperation("List templates")
    @Produces(MediaType.APPLICATION_JSON)
    List<TemplateEntry> list(@ApiParam @QueryParam("sortBy") @DefaultValue("name") String sortBy,
                             @ApiParam @QueryParam("asc") @DefaultValue("true") boolean asc);

    @PUT
    @ApiOperation("Update an existing template")
    @Path("/{name}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateTemplateResponse update(@ApiParam @PathParam("name") @ConcordKey String name,
                                  @ApiParam InputStream data);

    @DELETE
    @ApiOperation("Delete an existing template")
    @Path("/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteTemplateResponse delete(@ApiParam @PathParam("name") @ConcordKey String name);
}
