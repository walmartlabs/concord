package com.walmartlabs.concord.server.api.inventory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Api(value = "Inventory", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/inventory")
public interface InventoryQueryResource {

    /**
     * Returns inventory query
     *
     * @param inventoryName inventory's name
     * @param queryName query's name
     * @return query text
     */
    @GET
    @ApiOperation("Get inventory query")
    @Path("/{inventoryName}/query/{queryName}")
    @Produces(MediaType.APPLICATION_JSON)
    InventoryQueryEntry get(
            @ApiParam @PathParam("inventoryName") String inventoryName,
            @ApiParam @PathParam("queryName") String queryName);

    /**
     * Creates or updates inventory query
     *
     * @param inventoryName inventory's name
     * @param queryName query's name
     * @param text query text
     * @return
     */
    @POST
    @ApiOperation("Create or update inventory query")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{inventoryName}/query/{queryName}")
    CreateInventoryQueryResponse createOrUpdate(@ApiParam @PathParam("inventoryName")String inventoryName,
                                                @ApiParam @PathParam("queryName")String queryName,
                                                @ApiParam String text);

    /**
     * Deletes inventory query
     *
     * @param inventoryName inventory's name
     * @param queryName query's name
     * @return
     */
    @DELETE
    @ApiOperation("Delete inventory query")
    @Path("/{inventoryName}/query/{queryName}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteInventoryQueryResponse delete(@ApiParam @PathParam("inventoryName") String inventoryName,
                                        @ApiParam @PathParam("queryName") String queryName);

    /**
     * Executes inventory query
     *
     * @param inventoryName inventory's name
     * @param queryName query's name
     * @param params query params
     * @return query result
     */
    @POST
    @ApiOperation("Execute inventory query")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{inventoryName}/query/{queryName}/exec")
    List<Object> exec(@ApiParam @PathParam("inventoryName") String inventoryName,
                      @ApiParam @PathParam("queryName")String queryName,
                      @ApiParam @Valid Map<String, Object> params);
}
