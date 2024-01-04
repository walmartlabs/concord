package com.walmartlabs.concord.server.org;

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
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@Path("/api/v1/org")
@Tag(name = "Organizations")
public class OrganizationResource implements Resource {

    private final OrganizationDao orgDao;
    private final OrganizationManager orgManager;

    @Inject
    public OrganizationResource(OrganizationDao orgDao, OrganizationManager orgManager) {
        this.orgDao = orgDao;
        this.orgManager = orgManager;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Create or update an organization", operationId = "createOrUpdateOrg")
    public CreateOrganizationResponse createOrUpdate(@Valid OrganizationEntry entry) {
        OrganizationOperationResult result = orgManager.createOrUpdate(entry);
        return new CreateOrganizationResponse(result.orgId(), result.result());
    }

    @GET
    @Path("/{orgName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get an existing organization", operationId = "getOrg")
    public OrganizationEntry get(@PathParam("orgName") @ConcordKey String orgName) {
        return orgDao.getByName(orgName);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List organizations", operationId = "listOrgs")
    public List<OrganizationEntry> find(@QueryParam("onlyCurrent") @DefaultValue("false") boolean onlyCurrent,
                                        @QueryParam("offset") int offset,
                                        @QueryParam("limit") int limit,
                                        @QueryParam("filter") String filter) {

        UserPrincipal p = UserPrincipal.assertCurrent();
        UUID userId = p.getId();

        if (Roles.isAdmin() || Roles.isGlobalReader() || Roles.isGlobalWriter()) {
            // admins and global readers/writers see all orgs regardless of the onlyCurrent value
            userId = null;
        }

        return orgDao.list(userId, onlyCurrent, offset, limit, filter);
    }

    @DELETE
    @Path("/{orgName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Remove an existing organization", operationId = "deleteOrg")
    public GenericOperationResult delete(@PathParam("orgName") @ConcordKey String orgName,
                                         @QueryParam("confirmation") String confirmation) {

        if (!"yes".equalsIgnoreCase(confirmation)) {
            throw new ConcordApplicationException("Operation must be confirmed", Response.Status.BAD_REQUEST);
        }

        orgManager.delete(orgName);
        return new GenericOperationResult(OperationResult.DELETED);
    }
}
