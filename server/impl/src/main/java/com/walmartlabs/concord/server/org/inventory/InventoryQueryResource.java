package com.walmartlabs.concord.server.org.inventory;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Named
@Singleton
@Api(value = "Inventory Queries", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
@Deprecated
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
    @ApiOperation("Get inventory query")
    @Path("/{orgName}/inventory/{inventoryName}/query/{queryName}")
    @Produces(MediaType.APPLICATION_JSON)
    public InventoryQueryEntry get(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                   @ApiParam @PathParam("inventoryName") @ConcordKey String inventoryName,
                                   @ApiParam @PathParam("queryName") @ConcordKey String queryName) {

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
    @ApiOperation("Create or update inventory query")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{orgName}/inventory/{inventoryName}/query/{queryName}")
    public CreateInventoryQueryResponse createOrUpdate(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                       @ApiParam @PathParam("inventoryName") @ConcordKey String inventoryName,
                                                       @ApiParam @PathParam("queryName") @ConcordKey String queryName,
                                                       @ApiParam String text) {

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
    @ApiOperation(value = "List inventory queries", responseContainer = "list", response = InventoryQueryEntry.class)
    @Path("/{orgName}/inventory/{inventoryName}/query")
    @Produces(MediaType.APPLICATION_JSON)
    public List<InventoryQueryEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                          @ApiParam @PathParam("inventoryName") @ConcordKey String inventoryName) {

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
    @ApiOperation("Delete inventory query")
    @Path("/{orgName}/inventory/{inventoryName}/query/{queryName}")
    @Produces(MediaType.APPLICATION_JSON)
    public DeleteInventoryQueryResponse delete(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                               @ApiParam @PathParam("inventoryName") @ConcordKey String inventoryName,
                                               @ApiParam @PathParam("queryName") @ConcordKey String queryName) {

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
    @ApiOperation("Execute inventory query")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{orgName}/inventory/{inventoryName}/query/{queryName}/exec")
    @WithTimer
    public List<Object> exec(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                             @ApiParam @PathParam("inventoryName") @ConcordKey String inventoryName,
                             @ApiParam @PathParam("queryName") @ConcordKey String queryName,
                             @ApiParam @Valid Map<String, Object> params) {

        return storageQueryResource.exec(orgName, inventoryName, queryName, params);
    }

    private static InventoryQueryEntry convert(JsonStoreQueryEntry query) {
        if (query == null) {
            return null;
        }
        return new InventoryQueryEntry(query.id(), query.name(), query.storeId(), query.text());
    }
}
