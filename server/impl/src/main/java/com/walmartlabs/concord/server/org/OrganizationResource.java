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
import com.walmartlabs.concord.server.OperationResult;
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
        UUID orgId = entry.getId();
        if (orgId == null) {
            orgId = orgDao.getId(entry.getName());
        }

        if (orgId == null) {
            orgId = orgManager.create(entry);
            return new CreateOrganizationResponse(orgId, OperationResult.CREATED);
        } else {
            orgManager.update(orgId, entry);
            return new CreateOrganizationResponse(orgId, OperationResult.UPDATED);
        }
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
    public List<OrganizationEntry> list(@ApiParam @QueryParam("onlyCurrent") @DefaultValue("false") boolean onlyCurrent) {
        UUID userId = null;

        if (onlyCurrent) {
            UserPrincipal p = UserPrincipal.assertCurrent();
            if (!p.isAdmin() && !p.isGlobalReader() && !p.isGlobalWriter()) {
                userId = p.getId();
            }
        }

        return orgDao.list(userId);
    }
}
