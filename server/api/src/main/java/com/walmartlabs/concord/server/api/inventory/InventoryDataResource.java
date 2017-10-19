package com.walmartlabs.concord.server.api.inventory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

@Api("Inventory")
@Path("/api/v1/inventory")
public interface InventoryDataResource {

    /**
     * Returns an existing inventory data.
     *
     * @param inventoryName inventory's name
     * @param itemPath data item path
     * @return
     */
    @GET
    @ApiOperation("Get inventory data")
    @Path("/{inventoryName}/data/{itemPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    Object get(@ApiParam @PathParam("inventoryName") String inventoryName,
               @ApiParam @PathParam("itemPath") String itemPath) throws Exception;

    /**
     * Modifies inventory data
     *
     * @param inventoryName inventory's name
     * @param itemPath inventory's data path
     * @param data inventory's data
     * @return full inventory data by path
     */
    @POST
    @ApiOperation("Modify inventory data")
    @Path("/{inventoryName}/data/{itemPath:.*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Object data(@ApiParam @PathParam("inventoryName") String inventoryName,
                @ApiParam @PathParam("itemPath") String itemPath,
                @ApiParam Object data) throws Exception;

    /**
     * Deletes inventory data
     *
     * @param inventoryName inventory's name
     * @param itemPath inventory's data path
     * @return
     */
    @DELETE
    @ApiOperation("Delete inventory data")
    @Path("/{inventoryName}/data/{itemPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteInventoryDataResponse delete(@ApiParam @PathParam("inventoryName") String inventoryName,
                                       @ApiParam @PathParam("itemPath") String itemPath);
}
