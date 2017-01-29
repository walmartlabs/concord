package com.walmartlabs.concord.server.api.template;

import com.walmartlabs.concord.common.validation.ConcordId;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;

@Path("/api/v1/template")
public interface TemplateResource {

    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    CreateTemplateResponse create(@QueryParam("name") @NotNull @ConcordKey String name, InputStream data);

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateTemplateResponse update(@PathParam("id") @ConcordId String id, InputStream data);

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteTemplateResponse delete(@PathParam("id") @ConcordId String id);
}
