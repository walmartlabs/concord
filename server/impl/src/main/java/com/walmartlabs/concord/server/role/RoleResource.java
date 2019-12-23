package com.walmartlabs.concord.server.role;

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
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.sdk.audit.AuditObject;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.user.RoleEntry;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

import static javax.ws.rs.core.Response.Status;

@Named
@Singleton
@Api(value = "Roles", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/role")
public class RoleResource implements Resource {

    private final RoleDao roleDao;
    private final AuditLog auditLog;

    @Inject
    public RoleResource(RoleDao roleDao, AuditLog auditLog) {
        this.roleDao = roleDao;
        this.auditLog = auditLog;
    }

    @GET
    @ApiOperation("Get a role's details")
    @Path("/{roleName}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public RoleEntry get(@ApiParam @PathParam("roleName") String roleName) {
        assertAdmin();

        UUID id = roleDao.getId(roleName);
        if (id == null) {
            throw new ConcordApplicationException("Role not found: " + roleName, Status.NOT_FOUND);
        }

        return roleDao.get(id);
    }

    @GET
    @ApiOperation(value = "List roles", responseContainer = "list", response = RoleEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<RoleEntry> list() {
        assertAdmin();

        return roleDao.list();
    }

    @POST
    @ApiOperation("Create or update a role")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RoleOperationResponse createOrUpdate(@ApiParam @Valid RoleEntry entry) {
        assertAdmin();

        UUID id = entry.getId();
        if (id == null && entry.getName() != null) {
            id = roleDao.getId(entry.getName());
        }

        if (id == null) {
            id = roleDao.insert(entry.getName(), entry.getPermissions());

            auditLog.add(AuditObject.ROLE, AuditAction.CREATE)
                    .field("id", id)
                    .field("name", entry.getName())
                    .field("permissions", entry.getPermissions())
                    .log();

            return new RoleOperationResponse(id, OperationResult.CREATED);
        } else {
            roleDao.update(id, entry.getName(), entry.getPermissions());

            auditLog.add(AuditObject.ROLE, AuditAction.UPDATE)
                    .field("id", id)
                    .field("name", entry.getName())
                    .field("permissions", entry.getPermissions())
                    .log();

            return new RoleOperationResponse(id, OperationResult.UPDATED);
        }
    }

    @DELETE
    @ApiOperation("Delete an existing role")
    @Path("/{roleName}")
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult delete(@ApiParam @PathParam("roleName") @ConcordKey String roleName) {
        assertAdmin();

        UUID id = roleDao.getId(roleName);
        if (id == null) {
            throw new ConcordApplicationException("Role not found: " + roleName, Status.NOT_FOUND);
        }

        roleDao.delete(id);

        auditLog.add(AuditObject.ROLE, AuditAction.DELETE)
                .field("id", id)
                .field("name", roleName)
                .log();

        return new GenericOperationResult(OperationResult.DELETED);
    }

    private static void assertAdmin() {
        if (!Roles.isAdmin()) {
            throw new UnauthorizedException("Only admins can do that");
        }
    }
}
