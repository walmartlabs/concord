package com.walmartlabs.concord.server.org.jsonstore;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/v1/org")
@Tag(name = "JsonStoreData")
public class JsonStoreDataResource implements Resource {

    private final JsonStoreDataManager storeDataManager;

    @Inject
    public JsonStoreDataResource(JsonStoreDataManager storeDataManager) {
        this.storeDataManager = storeDataManager;
    }

    /**
     * Returns an existing store data.
     *
     * @param orgName   organization's name
     * @param storeName store's name
     * @param itemPath  data item path
     * @return
     */
    @GET
    @Path("/{orgName}/jsonstore/{storeName}/item/{itemPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get store data", operationId = "getJsonStoreData")
    public Object get(@PathParam("orgName") String orgName,
                      @PathParam("storeName") String storeName,
                      @PathParam("itemPath") String itemPath) {

        return storeDataManager.getItem(orgName, storeName, itemPath);
    }

    /**
     * List items in a store.
     *
     * @param orgName   organization's name
     * @param storeName store's name
     * @return
     */
    @GET
    @Path("/{orgName}/jsonstore/{storeName}/item")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List items in a JSON store", operationId = "listJsonStoreData")
    public List<String> list(@PathParam("orgName") String orgName,
                             @PathParam("storeName") String storeName,
                             @QueryParam("offset") @DefaultValue("0") int offset,
                             @QueryParam("limit") @DefaultValue("30") int limit,
                             @QueryParam("filter") String filter) {

        return storeDataManager.listItems(orgName, storeName, offset, limit, filter);
    }

    /**
     * Update an item in a store.
     *
     * @param orgName   organization's name
     * @param storeName store's name
     * @param itemPath  store's data path
     * @param data      store's data, must be a valid JSON object (represented by a Map)
     * @return
     */
    @PUT
    @Path("/{orgName}/jsonstore/{storeName}/item/{itemPath:.*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Update an item in a store", operationId = "updateJsonStoreData")
    public GenericOperationResult data(@PathParam("orgName") String orgName,
                                       @PathParam("storeName") String storeName,
                                       @PathParam("itemPath") String itemPath,
                                       Object data) {

        OperationResult result = storeDataManager.createOrUpdate(orgName, storeName, itemPath, data);

        return new GenericOperationResult(result);
    }

    /**
     * Same as {@link #data(String, String, String, Object)}.
     */
    @POST
    @Path("/{orgName}/jsonstore/{storeName}/item/{itemPath:.*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult update(@PathParam("orgName") String orgName,
                                         @PathParam("storeName") String storeName,
                                         @PathParam("itemPath") String itemPath,
                                         Object data) {

        return data(orgName, storeName, itemPath, data);
    }

    /**
     * Remove an item from a store.
     *
     * @param orgName   organization's name
     * @param storeName store's name
     * @param itemPath  store's data path
     * @return
     */
    @DELETE
    @Path("/{orgName}/jsonstore/{storeName}/item/{itemPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Remove an item from a store", operationId = "deleteJsonStoreDataItem")
    public GenericOperationResult delete(@PathParam("orgName") String orgName,
                                         @PathParam("storeName") String storeName,
                                         @PathParam("itemPath") String itemPath) {

        boolean deleted = storeDataManager.delete(orgName, storeName, itemPath);
        return new GenericOperationResult(deleted ? OperationResult.DELETED : OperationResult.NOT_FOUND);
    }
}
