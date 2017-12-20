package com.walmartlabs.concord.server.api.org.inventory;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

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
@Path("/api/v1/org")
public interface InventoryQueryResource {

    /**
     * Returns inventory query
     *
     * @param orgName organization's name
     * @param inventoryName inventory's name
     * @param queryName query's name
     * @return query text
     */
    @GET
    @ApiOperation("Get inventory query")
    @Path("/{orgName}/inventory/{inventoryName}/query/{queryName}")
    @Produces(MediaType.APPLICATION_JSON)
    InventoryQueryEntry get(@ApiParam @PathParam("orgName") String orgName,
                            @ApiParam @PathParam("inventoryName") String inventoryName,
                            @ApiParam @PathParam("queryName") String queryName);

    /**
     * Creates or updates inventory query
     *
     * @param orgName organization's name
     * @param inventoryName inventory's name
     * @param queryName query's name
     * @param text query text
     * @return
     */
    @POST
    @ApiOperation("Create or update inventory query")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{orgName}/inventory/{inventoryName}/query/{queryName}")
    CreateInventoryQueryResponse createOrUpdate(@ApiParam @PathParam("orgName") String orgName,
                                                @ApiParam @PathParam("inventoryName")String inventoryName,
                                                @ApiParam @PathParam("queryName")String queryName,
                                                @ApiParam String text);

    /**
     * Deletes inventory query
     *
     * @param orgName organization's name
     * @param inventoryName inventory's name
     * @param queryName query's name
     * @return
     */
    @DELETE
    @ApiOperation("Delete inventory query")
    @Path("/{orgName}/inventory/{inventoryName}/query/{queryName}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteInventoryQueryResponse delete(@ApiParam @PathParam("orgName") String orgName,
                                        @ApiParam @PathParam("inventoryName") String inventoryName,
                                        @ApiParam @PathParam("queryName") String queryName);

    /**
     * Executes inventory query
     *
     * @param orgName organization's name
     * @param inventoryName inventory's name
     * @param queryName query's name
     * @param params query params
     * @return query result
     */
    @POST
    @ApiOperation("Execute inventory query")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{orgName}/inventory/{inventoryName}/query/{queryName}/exec")
    List<Object> exec(@ApiParam @PathParam("orgName") String orgName,
                      @ApiParam @PathParam("inventoryName") String inventoryName,
                      @ApiParam @PathParam("queryName")String queryName,
                      @ApiParam @Valid Map<String, Object> params);
}
