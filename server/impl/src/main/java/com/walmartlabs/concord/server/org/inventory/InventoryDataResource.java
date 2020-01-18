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

import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.jsonstore.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
@Singleton
@Api(value = "Inventory Data", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
@Deprecated
public class InventoryDataResource implements Resource {

    private final JsonStoreDataResource storageDataResource;
    private final OrganizationManager orgManager;
    private final JsonStoreAccessManager accessManager;
    private final JsonStoreDataDao storageDataDao;

    @Inject
    public InventoryDataResource(JsonStoreDataResource storageDataResource,
                                 OrganizationManager orgManager,
                                 JsonStoreAccessManager jsonStoreAccessManager,
                                 JsonStoreDataDao storageDataDao) {

        this.storageDataResource = storageDataResource;
        this.orgManager = orgManager;
        this.accessManager = jsonStoreAccessManager;
        this.storageDataDao = storageDataDao;
    }

    /**
     * Returns an existing inventory data.
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @param itemPath      data item path
     * @return
     */
    @GET
    @ApiOperation("Get inventory data")
    @Path("/{orgName}/inventory/{inventoryName}/data/{itemPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Object get(@ApiParam @PathParam("orgName") String orgName,
                      @ApiParam @PathParam("inventoryName") String inventoryName,
                      @ApiParam @PathParam("itemPath") String itemPath,
                      @ApiParam @QueryParam("singleItem") @DefaultValue("false") boolean singleItem) {

        return storageDataResource.get(orgName, inventoryName, itemPath);
    }

    /**
     * Returns all inventory data for inventory.
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @return
     */
    @GET
    @ApiOperation("List inventory data")
    @Path("/{orgName}/inventory/{inventoryName}/data")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Map<String, Object>> list(@ApiParam @PathParam("orgName") String orgName,
                                          @ApiParam @PathParam("inventoryName") String inventoryName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry storage = accessManager.assertAccess(org.getId(), null, inventoryName, ResourceAccessLevel.READER, true);

        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonStoreDataEntry storageDataEntry : storageDataDao.list(storage.id())) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("data", storageDataEntry.data());
            entry.put("path", storageDataEntry.path());
            result.add(entry);
        }
        return result;
    }

    /**
     * Modifies inventory data
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @param itemPath      inventory's data path
     * @param data          inventory's data
     * @return full inventory data by path
     */
    @POST
    @ApiOperation("Modify inventory data")
    @Path("/{orgName}/inventory/{inventoryName}/data/{itemPath:.*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Object data(@ApiParam @PathParam("orgName") String orgName,
                       @ApiParam @PathParam("inventoryName") String inventoryName,
                       @ApiParam @PathParam("itemPath") String itemPath,
                       @ApiParam Object data) {

        storageDataResource.data(orgName, inventoryName, itemPath, data);
        return data;
    }

    /**
     * Deletes inventory data
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @param itemPath      inventory's data path
     * @return
     */
    @DELETE
    @ApiOperation("Delete inventory data")
    @Path("/{orgName}/inventory/{inventoryName}/data/{itemPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public DeleteInventoryDataResponse delete(@ApiParam @PathParam("orgName") String orgName,
                                              @ApiParam @PathParam("inventoryName") String inventoryName,
                                              @ApiParam @PathParam("itemPath") String itemPath) {

        storageDataResource.delete(orgName, inventoryName, itemPath);

        return new DeleteInventoryDataResponse();
    }
}
