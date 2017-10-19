package com.walmartlabs.concord.server.api.inventory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Api("Inventory")
@Path("/api/v1/inventory")
public interface InventoryResource {

    /**
     * Returns an existing inventory.
     *
     * @param inventoryName inventory's name
     * @return inventory
     */
    @GET
    @ApiOperation("Get existing inventory")
    @Path("/{inventoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    InventoryEntry get(@ApiParam @PathParam("inventoryName") String inventoryName);

    /**
     * Create or update a inventory.
     *
     * @param entry inventory's data
     * @return
     */
    @POST
    @ApiOperation("Create or update inventory")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateInventoryResponse createOrUpdate(@ApiParam @Valid InventoryEntry entry);

    /**
     * Creates a new inventory or updates an existing one.
     *
     * @param inventoryName inventory's name
     * @return
     */
    @DELETE
    @ApiOperation("Delete inventory")
    @Path("/{inventoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteInventoryResponse delete(@ApiParam @PathParam("inventoryName") String inventoryName);
}
