package com.walmartlabs.concord.server.api.org.inventory;

import com.walmartlabs.concord.server.api.GenericOperationResultResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Api(value = "Inventory", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public interface InventoryResource {

    /**
     * Returns an existing inventory.
     *
     * @param orgName organization's name
     * @param inventoryName inventory's name
     * @return inventory
     */
    @GET
    @ApiOperation("Get existing inventory")
    @Path("/{orgName}/inventory/{inventoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    InventoryEntry get(@ApiParam @PathParam("orgName") String orgName,
                       @ApiParam @PathParam("inventoryName") String inventoryName);

    /**
     * Create or update a inventory.
     *
     * @param orgName organization's name
     * @param entry inventory's data
     * @return
     */
    @POST
    @ApiOperation("Create or update inventory")
    @Path("/{orgName}/inventory")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateInventoryResponse createOrUpdate(@ApiParam @PathParam("orgName") String orgName,
                                           @ApiParam @Valid InventoryEntry entry);

    /**
     * Creates a new inventory or updates an existing one.
     *
     * @param orgName organization's name
     * @param inventoryName inventory's name
     * @return
     */
    @DELETE
    @ApiOperation("Delete inventory")
    @Path("/{orgName}/inventory/{inventoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResultResponse delete(@ApiParam @PathParam("orgName") String orgName,
                                          @ApiParam @PathParam("inventoryName") String inventoryName);
}
