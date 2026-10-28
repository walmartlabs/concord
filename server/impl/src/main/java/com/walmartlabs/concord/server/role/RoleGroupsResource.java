package com.walmartlabs.concord.server.role;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Path("/api/v2/role/{roleName}/groups/{sourceId}")
public class RoleGroupsResource implements Resource {
    private final RoleDao dao;
    private final AuthorizationService authorization;
    private final AuditLog audit;

    @Inject
    public RoleGroupsResource(RoleDao dao, AuthorizationService authorization, AuditLog audit) {
        this.dao = dao;
        this.authorization = authorization;
        this.audit = audit;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listRoleSourceGroups")
    public List<String> list(@PathParam("roleName") String roleName, @PathParam("sourceId") String sourceId) {
        assertAdmin();
        validateSource(sourceId);
        return dao.listGroups(roleId(roleName), sourceId);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "putRoleSourceGroups")
    public GenericOperationResult put(@PathParam("roleName") String roleName, @PathParam("sourceId") String sourceId,
                                      @QueryParam("replace") @DefaultValue("false") boolean replace, Collection<String> groups) {
        assertAdmin();
        validateSource(sourceId);
        if (groups == null || (!replace && groups.isEmpty()) || groups.stream().anyMatch(g -> g == null || g.isBlank())) {
            throw new ValidationErrorsException("Invalid group list");
        }
        UUID id = roleId(roleName);
        dao.tx(tx -> {
            if (replace) dao.removeGroups(tx, id, sourceId);
            for (String group : groups) dao.upsertGroup(tx, id, sourceId, group);
        });
        audit.add(AuditObject.ROLE, AuditAction.UPDATE).field("roleId", id).field("name", roleName)
                .field("action", "addGroups").field("sourceId", sourceId).field("groups", groups).field("replace", replace).log();
        return new GenericOperationResult(OperationResult.UPDATED);
    }

    private UUID roleId(String name) {
        UUID id = dao.getId(name);
        if (id == null) throw new ConcordApplicationException("Role not found: " + name, Response.Status.NOT_FOUND);
        return id;
    }
    private void validateSource(String sourceId) {
        if (!authorization.isActiveSource(sourceId)) throw new ValidationErrorsException("Unknown or inactive authorization source: " + sourceId);
    }

    private static void assertAdmin() {
        if (!Roles.isAdmin()) throw new UnauthorizedException("Only admins can do that");
    }
}
