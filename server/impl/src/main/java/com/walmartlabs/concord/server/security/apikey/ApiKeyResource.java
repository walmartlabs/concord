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

import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.naming.NamingException;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Named
@Singleton
@Api(value = "API keys", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/apikey")
public class ApiKeyResource implements Resource {

    private final ApiKeyDao apiKeyDao;
    private final UserManager userManager;
    private final LdapManager ldapManager;

    @Inject
    public ApiKeyResource(ApiKeyDao apiKeyDao, UserManager userManager, LdapManager ldapManager) {
        this.apiKeyDao = apiKeyDao;
        this.userManager = userManager;
        this.ldapManager = ldapManager;
    }

    @POST
    @ApiOperation("Create a new API key")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public CreateApiKeyResponse create(@ApiParam @Valid CreateApiKeyRequest req) {
        UUID userId = assertUserId(req.getUserId());
        if (userId == null) {
            userId = assertUsername(req.getUsername(), req.getUserType());
        }

        if (userId == null) {
            throw new ValidationErrorsException("User ID or name is required");
        }

        assertOwner(userId);

        String key = apiKeyDao.newApiKey();
        UUID id = apiKeyDao.insert(userId, key);
        return new CreateApiKeyResponse(id, key);
    }

    @DELETE
    @ApiOperation("Delete an existing API key")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public DeleteApiKeyResponse delete(@ApiParam @PathParam("id") UUID id) {
        UUID userId = apiKeyDao.getUserId(id);
        if (userId == null) {
            throw new ValidationErrorsException("API key not found: " + id);
        }

        assertOwner(userId);

        apiKeyDao.delete(id);
        return new DeleteApiKeyResponse();
    }

    private UUID assertUsername(String username, UserType type) {
        if (username == null) {
            return null;
        }

        if (!userManager.getId(username).isPresent()) {
            try {
                // TODO add a simpler "isExists" call
                if (ldapManager.getPrincipal(username) == null) {
                    throw new ValidationErrorsException("LDAP user not found: " + username);
                }
            } catch (NamingException e) {
                throw new WebApplicationException(e);
            }
        }

        if (type == null) {
            type = UserPrincipal.assertCurrent().getType();
        }

        UserEntry entry = userManager.getOrCreate(username, type);
        return entry.getId();
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

    private void assertOwner(UUID userId) {
        UserPrincipal p = UserPrincipal.assertCurrent();
        if (p.isAdmin()) {
            // admin users can manage other user's keys
            return;
        }

        if (!userId.equals(p.getId())) {
            throw new UnauthorizedException("Operation is not permitted");
        }
    }
}
