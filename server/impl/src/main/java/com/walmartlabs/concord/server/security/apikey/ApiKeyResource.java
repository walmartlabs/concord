package com.walmartlabs.concord.server.security.apikey;

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
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.cfg.ApiKeyConfiguration;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.shiro.authz.AuthorizationException;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Path("/api/v1/apikey")
@Tag(name = "API keys")
public class ApiKeyResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyResource.class);

    private final ApiKeyConfiguration cfg;
    private final ApiKeyDao apiKeyDao;
    private final UserManager userManager;
    private final AuditLog auditLog;

    @Inject
    public ApiKeyResource(ApiKeyConfiguration cfg, ApiKeyDao apiKeyDao, UserManager userManager, AuditLog auditLog) {
        this.cfg = cfg;
        this.apiKeyDao = apiKeyDao;
        this.userManager = userManager;
        this.auditLog = auditLog;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "List user api keys", operationId = "listUserApiKeys")
    public List<ApiKeyEntry> list(@QueryParam("userId") UUID requestUserId) {
        UUID userId = requestUserId;
        if (userId == null) {
            userId = UserPrincipal.assertCurrent().getId();
        }

        assertOwner(userId);

        return apiKeyDao.list(userId);
    }

    @POST
    @Path("/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Create a new API key", operationId = "createApiKey")
    public CreateApiKeyResponse create(@PathParam("name") @ConcordKey String name) {
        assertAdmin();

        if (apiKeyDao.getId(null, name) != null) {
            throw new ValidationErrorsException("API Token with name '" + name + "' already exists");
        }

        return createApiKey(null, name, null);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Create a new API key", operationId = "createUserApiKey")
    public CreateApiKeyResponse create(@Valid CreateApiKeyRequest req) {
        UUID userId = assertUserId(req.getUserId());
        if (userId == null) {
            userId = assertUsername(req.getUsername(), req.getUserDomain(), req.getUserType());
        }

        if (userId == null) {
            userId = UserPrincipal.assertCurrent().getId();
        }

        assertOwner(userId);

        String name = trim(req.getName());
        if (name == null || name.isEmpty()) {
            // auto generate the name
            name = "key#" + UUID.randomUUID();
        }

        if (apiKeyDao.getId(userId, name) != null) {
            throw new ValidationErrorsException("API Token with name '" + name + "' already exists");
        }

        String key = req.getKey();

        return createApiKey(userId, name, key);
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Delete an existing API key", operationId = "deleteUserApiKeyById")
    public GenericOperationResult deleteKeyById(@PathParam("id") UUID id) {
        UUID userId = apiKeyDao.getUserId(id);
        if (userId == null) {
            throw new ValidationErrorsException("API key not found: " + id);
        }

        assertOwner(userId);

        apiKeyDao.delete(id);

        auditLog.add(AuditObject.API_KEY, AuditAction.DELETE)
                .field("id", id)
                .log();

        return new GenericOperationResult(OperationResult.DELETED);
    }

    private CreateApiKeyResponse createApiKey(UUID userId, String name, @Nullable String key) {
        if (key == null) {
            key = apiKeyDao.newApiKey();
        }

        OffsetDateTime expiredAt = null;
        if (cfg.isExpirationEnabled()) {
            expiredAt = OffsetDateTime.now().plusDays(cfg.getExpirationPeriod().toDays());
        }

        UUID id;
        try {
            id = apiKeyDao.insert(userId, key, name, expiredAt);
        } catch (DataAccessException e) {
            if (PgUtils.isUniqueViolationError(e)) {
                log.warn("create ['{}'] -> duplicate name error: {}", name, e.getMessage());
                throw new ValidationErrorsException("Duplicate API key name: " + name);
            }

            throw e;
        }

        auditLog.add(AuditObject.API_KEY, AuditAction.CREATE)
                .field("id", id)
                .field("name", name)
                .field("expiredAt", expiredAt)
                .field("userId", userId)
                .log();

        return new CreateApiKeyResponse(id, key);
    }

    private UUID assertUsername(String username, String domain, UserType type) {
        if (username == null) {
            return null;
        }

        if (type == null) {
            type = UserPrincipal.assertCurrent().getType();
        }

        return userManager.getId(username, domain, type)
                .orElseThrow(() -> new ConcordApplicationException("User not found: " + username));
    }

    private UUID assertUserId(UUID userId) {
        if (userId == null) {
            return null;
        }

        if (!userManager.get(userId).isPresent()) {
            throw new ValidationErrorsException("User not found: " + userId);
        }

        return userId;
    }

    private static void assertOwner(UUID userId) {
        if (Roles.isAdmin()) {
            // admin users can manage other user's keys
            return;
        }

        UserPrincipal p = UserPrincipal.assertCurrent();
        if (!userId.equals(p.getId())) {
            throw new UnauthorizedException("Operation is not permitted");
        }
    }

    private static void assertAdmin() {
        if (!Roles.isAdmin()) {
            throw new AuthorizationException("Only admins are allowed to update organizations");
        }
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }

        return s.trim();
    }
}
