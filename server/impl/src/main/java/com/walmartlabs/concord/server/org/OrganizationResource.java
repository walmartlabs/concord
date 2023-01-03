package com.walmartlabs.concord.server.org;

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
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
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
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@Named
@Singleton
@Api(value = "Organizations", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public class OrganizationResource implements Resource {

    private final OrganizationDao orgDao;
    private final OrganizationManager orgManager;

    @Inject
    public OrganizationResource(OrganizationDao orgDao, OrganizationManager orgManager) {
        this.orgDao = orgDao;
        this.orgManager = orgManager;
    }

    @POST
    @ApiOperation("Create or update an organization")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CreateOrganizationResponse createOrUpdate(@ApiParam @Valid OrganizationEntry entry) {
        OrganizationOperationResult result = orgManager.createOrUpdate(entry);
        return new CreateOrganizationResponse(result.orgId(), result.result());
    }

    @GET
    @Path("/{orgName}")
    @ApiOperation("Get an existing organization")
    @Produces(MediaType.APPLICATION_JSON)
    public OrganizationEntry get(@ApiParam @PathParam("orgName") @ConcordKey String orgName) {
        return orgDao.getByName(orgName);
    }

    @GET
    @ApiOperation(value = "List organizations", responseContainer = "list", response = OrganizationEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
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
    @ApiOperation("Remove an existing organization")
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult delete(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                         @ApiParam @QueryParam("confirmation") String confirmation) {

        if (!"yes".equalsIgnoreCase(confirmation)) {
            throw new ConcordApplicationException("Operation must be confirmed", Response.Status.BAD_REQUEST);
        }

        orgManager.delete(orgName);
        return new GenericOperationResult(OperationResult.DELETED);
    }
}
