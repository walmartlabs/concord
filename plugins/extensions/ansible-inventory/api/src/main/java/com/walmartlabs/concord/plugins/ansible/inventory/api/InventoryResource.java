package com.walmartlabs.concord.plugins.ansible.inventory.api;

import com.walmartlabs.concord.common.validation.ConcordId;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.List;

@Path("/api/v1/ansible/inventory")
public interface InventoryResource {

    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    CreateInventoryResponse create(@QueryParam("name") @NotNull @ConcordKey String name, InputStream data);

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    InventoryEntry get(@PathParam("id") @ConcordId String id);

    @GET
    @Path("/{id}/data")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    InputStream getData(@PathParam("id") @ConcordId String id);

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateInventoryResponse update(@PathParam("id") @ConcordId String id, InputStream data);

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteInventoryResponse delete(@PathParam("id") @ConcordId String id);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<InventoryEntry> list(@QueryParam("sortBy") @DefaultValue("name") String sortBy,
                              @QueryParam("asc") @DefaultValue("true") boolean asc);
}
