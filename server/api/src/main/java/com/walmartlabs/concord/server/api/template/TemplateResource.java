package com.walmartlabs.concord.server.api.template;

import com.walmartlabs.concord.common.validation.ConcordId;
import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;

@Api("Template")
@Path("/api/v1/template")
public interface TemplateResource {

    @POST
    @ApiOperation("Upload a new template")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    CreateTemplateResponse create(@ApiParam @QueryParam("name") @NotNull @ConcordKey String name,
                                  @ApiParam InputStream data);

    @PUT
    @ApiOperation("Update an existing template")
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateTemplateResponse update(@ApiParam @PathParam("id") @ConcordId String id,
                                  @ApiParam InputStream data);

    @DELETE
    @ApiOperation("Delete an existing template")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteTemplateResponse delete(@ApiParam @PathParam("id") @ConcordId String id);
}
