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
import io.swagger.v3.oas.annotations.Parameter;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Named
@Singleton
//@Api(value = "JsonStoreData", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
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
//    @ApiOperation("Get store data")
    @Path("/{orgName}/jsonstore/{storeName}/item/{itemPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Object get(@Parameter @PathParam("orgName") String orgName,
                      @Parameter @PathParam("storeName") String storeName,
                      @Parameter @PathParam("itemPath") String itemPath) {

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
//    @ApiOperation("List items in a JSON store")
    @Path("/{orgName}/jsonstore/{storeName}/item")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> list(@Parameter @PathParam("orgName") String orgName,
                             @Parameter @PathParam("storeName") String storeName,
                             @Parameter @QueryParam("offset") @DefaultValue("0") int offset,
                             @Parameter @QueryParam("limit") @DefaultValue("30") int limit,
                             @Parameter @QueryParam("filter") String filter) {

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
//    @ApiOperation("Update an item in a store")
    @Path("/{orgName}/jsonstore/{storeName}/item/{itemPath:.*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult data(@Parameter @PathParam("orgName") String orgName,
                                       @Parameter @PathParam("storeName") String storeName,
                                       @Parameter @PathParam("itemPath") String itemPath,
                                       @Parameter Object data) {

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
    public GenericOperationResult update(@Parameter @PathParam("orgName") String orgName,
                                         @Parameter @PathParam("storeName") String storeName,
                                         @Parameter @PathParam("itemPath") String itemPath,
                                         @Parameter Object data) {

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
//    @ApiOperation("Remove an item from a store")
    @Path("/{orgName}/jsonstore/{storeName}/item/{itemPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult delete(@Parameter @PathParam("orgName") String orgName,
                                         @Parameter @PathParam("storeName") String storeName,
                                         @Parameter @PathParam("itemPath") String itemPath) {

        boolean deleted = storeDataManager.delete(orgName, storeName, itemPath);
        return new GenericOperationResult(deleted ? OperationResult.DELETED : OperationResult.NOT_FOUND);
    }
}
