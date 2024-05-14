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

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/api/v1/org")
@Tag(name = "JsonStoreQuery")
public class JsonStoreQueryResource implements Resource {

    private final JsonStoreQueryManager storeQueryManager;

    @Inject
    public JsonStoreQueryResource(JsonStoreQueryManager storeQueryManager) {
        this.storeQueryManager = storeQueryManager;
    }

    /**
     * Returns an existing JSON store query
     *
     * @param orgName   organization's name
     * @param storeName store's name
     * @param queryName query's name
     * @return query text
     */
    @GET
    @Path("/{orgName}/jsonstore/{storeName}/query/{queryName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get an existing JSON store query", operationId = "getJsonStoreQuery")
    public JsonStoreQueryEntry get(@PathParam("orgName") @ConcordKey String orgName,
                                   @PathParam("storeName") @ConcordKey String storeName,
                                   @PathParam("queryName") @ConcordKey String queryName) {

        return storeQueryManager.get(orgName, storeName, queryName);
    }

    /**
     * Creates or updates a JSON store query
     *
     * @param orgName   organization's name
     * @param storeName store's name
     * @param entry     the query's entry
     * @return
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{orgName}/jsonstore/{storeName}/query")
    @Validate
    @Operation(description = "Create or update a JSON store query", operationId = "createOrUpdateJsonStoreQuery")
    public GenericOperationResult createOrUpdate(@PathParam("orgName") @ConcordKey String orgName,
                                                 @PathParam("storeName") @ConcordKey String storeName,
                                                 @Valid JsonStoreQueryRequest entry) {

        OperationResult result = storeQueryManager.createOrUpdate(orgName, storeName, entry);
        return new GenericOperationResult(result);
    }

    /**
     * List existing queries in a JSON store.
     *
     * @param orgName   organization's name
     * @param storeName store's name
     * @return
     */
    @GET
    @Path("/{orgName}/jsonstore/{storeName}/query")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List JSON Store queries", operationId = "listJsonStoreQueries")
    public List<JsonStoreQueryEntry> list(@PathParam("orgName") @ConcordKey String orgName,
                                          @PathParam("storeName") @ConcordKey String storeName,
                                          @QueryParam("offset") @DefaultValue("0") int offset,
                                          @QueryParam("limit") @DefaultValue("30") int limit,
                                          @QueryParam("filter") String filter) {

        return storeQueryManager.list(orgName, storeName, offset, limit, filter);
    }

    /**
     * Deletes an existing JSON query query
     *
     * @param orgName   organization's name
     * @param storeName store's name
     * @param queryName query's name
     * @return
     */
    @DELETE
    @Path("/{orgName}/jsonstore/{storeName}/query/{queryName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Delete an existing JSON query query", operationId = "deleteJsonStoreQuery")
    public GenericOperationResult delete(@PathParam("orgName") @ConcordKey String orgName,
                                         @PathParam("storeName") @ConcordKey String storeName,
                                         @PathParam("queryName") @ConcordKey String queryName) {

        storeQueryManager.delete(orgName, storeName, queryName);
        return new GenericOperationResult(OperationResult.DELETED);
    }

    /**
     * Executes an existing JSON store query
     *
     * @param orgName   organization's name
     * @param storeName store's name
     * @param queryName query's name
     * @param params    query params
     * @return query result
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{orgName}/jsonstore/{storeName}/query/{queryName}/exec")
    @WithTimer
    @Validate
    @Operation(description = "Execute an existing JSON store query", operationId = "execJsonStoreQuery")
    public List<Object> exec(@PathParam("orgName") @ConcordKey String orgName,
                             @PathParam("storeName") @ConcordKey String storeName,
                             @PathParam("queryName") @ConcordKey String queryName,
                             @Valid Map<String, Object> params) {

        try {
            return storeQueryManager.exec(orgName, storeName, queryName, params);
        } catch (ValidationErrorsException e) {
            throw e;
        } catch (Exception e) {
            throw new ConcordApplicationException("Error while executing a query: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a JSON store query.
     *
     * @param orgName   organization's name
     * @param storeName store's name
     * @param text      query's text
     */
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{orgName}/jsonstore/{storeName}/execQuery")
    @WithTimer
    public List<Object> execQuery(@PathParam("orgName") @ConcordKey String orgName,
                                  @PathParam("storeName") @ConcordKey String storeName,
                                  @QueryParam("maxLimit") @DefaultValue("10") int maxLimit,
                                  String text) {

        try {
            return storeQueryManager.exec(orgName, storeName, text, maxLimit);
        } catch (Exception e) {
            throw new ConcordApplicationException("Error while executing a query: " + e.getMessage(), e);
        }
    }
}
