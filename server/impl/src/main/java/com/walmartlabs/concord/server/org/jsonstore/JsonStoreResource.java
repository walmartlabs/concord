package com.walmartlabs.concord.server.org.jsonstore;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.List;

@Path("/api/v1/org")
@Tag(name = "JsonStore")
public class JsonStoreResource implements Resource {

    private final JsonStoreManager storeManager;

    @Inject
    public JsonStoreResource(JsonStoreManager storeManager) {
        this.storeManager = storeManager;
    }

    /**
     * List existing stores.
     *
     * @param orgName organization's name
     * @return list of stores
     */
    @GET
    @Path("/{orgName}/jsonstore")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List existing stores", operationId = "listJsonStores")
    public List<JsonStoreEntry> list(@PathParam("orgName") @ConcordKey String orgName,
                                     @QueryParam("offset") @DefaultValue("0") int offset,
                                     @QueryParam("limit") @DefaultValue("30") int limit,
                                     @QueryParam("filter") String filter) {

        return storeManager.list(orgName, offset, limit, filter);
    }

    /**
     * Returns an existing store.
     *
     * @param orgName   organization's name
     * @param storeName store's name
     * @return store
     */
    @GET
    @Path("/{orgName}/jsonstore/{storeName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get an existing store", operationId = "getJsonStore")
    public JsonStoreEntry get(@PathParam("orgName") @ConcordKey String orgName,
                              @PathParam("storeName") @ConcordKey String storeName) {

        return storeManager.get(orgName, storeName);
    }

    /**
     * Create or update a store.
     *
     * @param entry store's definition
     * @return
     */
    @POST
    @Path("/{orgName}/jsonstore")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Create or update a store", operationId = "createOrUpdateJsonStore")
    public GenericOperationResult createOrUpdate(@PathParam("orgName") @ConcordKey String orgName,
                                                 @Valid JsonStoreRequest entry) {

        OperationResult result = storeManager.createOrUpdate(orgName, entry);
        return new GenericOperationResult(result);
    }

    /**
     * Delete an existing store.
     *
     * @param storeName store's name
     * @return
     */
    @DELETE
    @Path("/{orgName}/jsonstore/{storeName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Delete an existing store", operationId = "deleteJsonStore")
    public GenericOperationResult delete(@PathParam("orgName") @ConcordKey String orgName,
                                         @PathParam("storeName") @ConcordKey String storeName) {

        storeManager.delete(orgName, storeName);
        return new GenericOperationResult(OperationResult.DELETED);
    }

    /**
     * Get the store's capacity.
     *
     * @param storeName store's name
     * @return
     */
    @GET
    @Path("/{orgName}/jsonstore/{storeName}/capacity")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get an existing store capacity", operationId = "getJsonStoreCapacity")
    public JsonStoreCapacity getCapacity(@PathParam("orgName") @ConcordKey String orgName,
                                         @PathParam("storeName") @ConcordKey String storeName) {

        return storeManager.getCapacity(orgName, storeName);
    }

    @GET
    @Path("/{orgName}/jsonstore/{storeName}/access")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Get a store's team access parameters", operationId = "getJsonStoreAccessLevel")
    public List<ResourceAccessEntry> getAccessLevel(@PathParam("orgName") @ConcordKey String orgName,
                                                    @PathParam("storeName") @ConcordKey String storeName) {

        return storeManager.getResourceAccess(orgName, storeName);
    }

    @POST
    @Path("/{orgName}/jsonstore/{storeName}/access/bulk")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Updates the access level for the specified store and team", operationId = "bulkUpdateJsonStoreAccessLevel")
    public GenericOperationResult updateAccessLevel(@PathParam("orgName") @ConcordKey String orgName,
                                                    @PathParam("storeName") @ConcordKey String storeName,
                                                    @Valid Collection<ResourceAccessEntry> entries) {

        if (entries == null) {
            throw new ConcordApplicationException("List of teams is null.", Response.Status.BAD_REQUEST);
        }

        storeManager.updateAccessLevel(orgName, storeName, entries, true);
        return new GenericOperationResult(OperationResult.UPDATED);
    }


    @POST
    @Path("/{orgName}/jsonstore/{storeName}/access")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Updates the access level for the specified store and team", operationId = "updateJsonStoreAccessLevel")
    public GenericOperationResult updateAccessLevel(@PathParam("orgName") @ConcordKey String orgName,
                                                    @PathParam("storeName") @ConcordKey String storeName,
                                                    @Valid ResourceAccessEntry entry) {

        storeManager.updateAccessLevel(orgName, storeName, entry);
        return new GenericOperationResult(OperationResult.UPDATED);
    }
}