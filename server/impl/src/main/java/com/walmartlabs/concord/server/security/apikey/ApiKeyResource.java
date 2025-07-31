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
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.shiro.authz.AuthorizationException;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

@Path("/api/v1/apikey")
@Tag(name = "API keys")
public class ApiKeyResource implements Resource {

    private final ApiKeyDao apiKeyDao;
    private final ApiKeyManager apiKeyManager;

    @Inject
    public ApiKeyResource(ApiKeyDao apiKeyDao, ApiKeyManager apiKeyManager) {
        this.apiKeyDao = requireNonNull(apiKeyDao);
        this.apiKeyManager = requireNonNull(apiKeyManager);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "List user api keys", operationId = "listUserApiKeys")
    public List<ApiKeyEntry> list(@QueryParam("userId") UUID userId) {
        return apiKeyManager.list(userId);
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

        return apiKeyManager.createApiKey(null, name, null);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Create a new API key", operationId = "createUserApiKey")
    public CreateApiKeyResponse create(@Valid CreateApiKeyRequest req) {
        return apiKeyManager.create(req);
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Delete an existing API key", operationId = "deleteUserApiKeyById")
    public GenericOperationResult deleteKeyById(@PathParam("id") UUID id) {
        apiKeyManager.deleteById(id);
        return new GenericOperationResult(OperationResult.DELETED);
    }

    private static void assertAdmin() {
        if (!Roles.isAdmin()) {
            throw new AuthorizationException("Only admins are allowed to create API keys without users");
        }
    }
}
