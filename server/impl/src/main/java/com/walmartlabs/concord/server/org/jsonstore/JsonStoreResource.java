package com.walmartlabs.concord.server.org.jsonstore;

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
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.List;

@Named
@Singleton
@Api(value = "JsonStore", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
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
    @ApiOperation(value = "List existing stores", responseContainer = "list", response = JsonStoreEntry.class)
    @Path("/{orgName}/jsonstore")
    @Produces(MediaType.APPLICATION_JSON)
    public List<JsonStoreEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                     @ApiParam @QueryParam("offset") @DefaultValue("0") int offset,
                                     @ApiParam @QueryParam("limit") @DefaultValue("30") int limit,
                                     @ApiParam @QueryParam("filter") String filter) {

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
    @ApiOperation("Get an existing store")
    @Path("/{orgName}/jsonstore/{storeName}")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonStoreEntry get(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                              @ApiParam @PathParam("storeName") @ConcordKey String storeName) {

        return storeManager.get(orgName, storeName);
    }

    /**
     * Create or update a store.
     *
     * @param entry store's definition
     * @return
     */
    @POST
    @ApiOperation("Create or update a store")
    @Path("/{orgName}/jsonstore")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public GenericOperationResult createOrUpdate(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                 @ApiParam @Valid JsonStoreRequest entry) {

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
    @ApiOperation("Delete an existing store")
    @Path("/{orgName}/jsonstore/{storeName}")
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult delete(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                         @ApiParam @PathParam("storeName") @ConcordKey String storeName) {

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
    public JsonStoreCapacity getCapacity(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                         @ApiParam @PathParam("storeName") @ConcordKey String storeName) {

        return storeManager.getCapacity(orgName, storeName);
    }

    @GET
    @ApiOperation("Get a store's team access parameters")
    @Path("/{orgName}/jsonstore/{storeName}/access")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public List<ResourceAccessEntry> getAccessLevel(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                    @ApiParam @PathParam("storeName") @ConcordKey String storeName) {

        return storeManager.getResourceAccess(orgName, storeName);
    }

    @POST
    @ApiOperation("Updates the access level for the specified store and team")
    @Path("/{orgName}/jsonstore/{storeName}/access/bulk")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public GenericOperationResult updateAccessLevel(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                    @ApiParam @PathParam("storeName") @ConcordKey String storeName,
                                                    @ApiParam @Valid Collection<ResourceAccessEntry> entries) {

        if (entries == null) {
            throw new ConcordApplicationException("List of teams is null.", Response.Status.BAD_REQUEST);
        }

        storeManager.updateAccessLevel(orgName, storeName, entries, true);
        return new GenericOperationResult(OperationResult.UPDATED);
    }


    @POST
    @ApiOperation("Updates the access level for the specified store")
    @Path("/{orgName}/jsonstore/{storeName}/access")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public GenericOperationResult updateAccessLevel(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                    @ApiParam @PathParam("storeName") @ConcordKey String storeName,
                                                    @ApiParam @Valid ResourceAccessEntry entry) {

        storeManager.updateAccessLevel(orgName, storeName, entry);
        return new GenericOperationResult(OperationResult.UPDATED);
    }
}
