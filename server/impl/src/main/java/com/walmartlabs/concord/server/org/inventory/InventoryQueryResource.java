package com.walmartlabs.concord.server.org.inventory;

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

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.jsonstore.*;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/v1/org")
@Deprecated
@Tag(name = "Inventory Queries")
public class InventoryQueryResource implements Resource {

    private final JsonStoreQueryResource storageQueryResource;
    private final OrganizationManager organizationManager;
    private final JsonStoreDao storageDao;
    private final JsonStoreQueryDao storageQueryDao;

    @Inject
    public InventoryQueryResource(JsonStoreQueryResource storageQueryResource, OrganizationManager organizationManager, JsonStoreDao storageDao, JsonStoreQueryDao storageQueryDao) {
        this.storageQueryResource = storageQueryResource;
        this.organizationManager = organizationManager;
        this.storageDao = storageDao;
        this.storageQueryDao = storageQueryDao;
    }

    /**
     * Returns inventory query
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @param queryName     query's name
     * @return query text
     */
    @GET
    @Path("/{orgName}/inventory/{inventoryName}/query/{queryName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get inventory query", operationId = "getInventoryQuery")
    public InventoryQueryEntry get(@PathParam("orgName") @ConcordKey String orgName,
                                   @PathParam("inventoryName") @ConcordKey String inventoryName,
                                   @PathParam("queryName") @ConcordKey String queryName) {

        return convert(storageQueryResource.get(orgName, inventoryName, queryName));
    }

    /**
     * Creates or updates inventory query
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @param queryName     query's name
     * @param text          query text
     * @return
     */
    @POST
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{orgName}/inventory/{inventoryName}/query/{queryName}")
    @Operation(description = "Create or update inventory query", operationId = "createOrUpdateInventoryQuery")
    public CreateInventoryQueryResponse createOrUpdate(@PathParam("orgName") @ConcordKey String orgName,
                                                       @PathParam("inventoryName") @ConcordKey String inventoryName,
                                                       @PathParam("queryName") @ConcordKey String queryName,
                                                       String text) {

        GenericOperationResult res = storageQueryResource.createOrUpdate(orgName, inventoryName, JsonStoreQueryRequest.builder()
                .name(queryName)
                .text(text)
                .build());

        OrganizationEntry org = organizationManager.assertExisting(null, orgName);
        JsonStoreEntry storage = storageDao.get(org.getId(), inventoryName);
        UUID id = storageQueryDao.getId(storage.id(), queryName);

        return new CreateInventoryQueryResponse(res.getResult(), id);
    }

    /**
     * List inventory queries.
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @return
     */
    @GET
    @Path("/{orgName}/inventory/{inventoryName}/query")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List inventory queries", operationId = "listInventoryQueries")
    public List<InventoryQueryEntry> list(@PathParam("orgName") @ConcordKey String orgName,
                                          @PathParam("inventoryName") @ConcordKey String inventoryName) {

        return storageQueryResource.list(orgName, inventoryName, -1, -1, null)
                .stream()
                .map(InventoryQueryResource::convert)
                .collect(Collectors.toList());
    }

    /**
     * Deletes inventory query
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @param queryName     query's name
     * @return
     */
    @DELETE
    @Path("/{orgName}/inventory/{inventoryName}/query/{queryName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Delete inventory query", operationId = "deleteInventoryQuery")
    public DeleteInventoryQueryResponse delete(@PathParam("orgName") @ConcordKey String orgName,
                                               @PathParam("inventoryName") @ConcordKey String inventoryName,
                                               @PathParam("queryName") @ConcordKey String queryName) {

        storageQueryResource.delete(orgName, inventoryName, queryName);
        return new DeleteInventoryQueryResponse();
    }

    /**
     * Executes inventory query
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @param queryName     query's name
     * @param params        query params
     * @return query result
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{orgName}/inventory/{inventoryName}/query/{queryName}/exec")
    @WithTimer
    @Validate
    @Operation(description = "Execute inventory query", operationId = "executeInventoryQuery")
    public List<Object> exec(@PathParam("orgName") @ConcordKey String orgName,
                             @PathParam("inventoryName") @ConcordKey String inventoryName,
                             @PathParam("queryName") @ConcordKey String queryName,
                             @Valid Map<String, Object> params) {

        return storageQueryResource.exec(orgName, inventoryName, queryName, params);
    }

    private static InventoryQueryEntry convert(JsonStoreQueryEntry query) {
        if (query == null) {
            return null;
        }
        return new InventoryQueryEntry(query.id(), query.name(), query.storeId(), query.text());
    }
}
