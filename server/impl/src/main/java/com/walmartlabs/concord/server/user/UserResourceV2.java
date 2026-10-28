package com.walmartlabs.concord.server.user;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.PrincipalNormalization;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.security.AuthorizationSubjectNotFoundException;
import com.walmartlabs.concord.server.sdk.security.AuthorizationUnavailableException;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.security.identity.IdentityDao;
import com.walmartlabs.concord.server.security.identity.IdentityDao.IdentityRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.NoSuchElementException;
import java.util.List;
import java.util.UUID;

@Path("/api/v2/user")
@Tag(name = "UserV2")
public class UserResourceV2 implements Resource {

    private final UserDao userDao;
    private final IdentityDao identityDao;
    private final AuthorizationService authorization;
    private final AccountSelector selectors;

    @Inject
    public UserResourceV2(UserDao userDao, IdentityDao identityDao, AuthorizationService authorization, AccountSelector selectors) {
        this.userDao = userDao;
        this.identityDao = identityDao;
        this.authorization = authorization;
        this.selectors = selectors;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Returns a list of existing active users matching the supplied filter", operationId = "listUsersWithFilter")
    public List<UserEntry> list(@QueryParam("offset") @DefaultValue("0") int offset,
                                @QueryParam("limit") @DefaultValue("10") int limit,
                                @QueryParam("filter") String filter) {

        if (offset < 0) {
            throw new ValidationErrorsException("'offset' must be a positive number or zero");
        }

        if (limit < 1) {
            throw new ValidationErrorsException("'limit' must be a positive number");
        }

        List<UserEntry> users = userDao.list(filter, offset, limit);
        users.forEach(this::project);
        return users;
    }

    @GET
    @Path("/identity")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getUserByIdentity")
    public UserEntry getUserByIdentity(@QueryParam("providerId") String providerId,
                                       @QueryParam("authority") String authority,
                                       @QueryParam("subject") String subject) {
        if (providerId == null || authority == null || subject == null) {
            throw new ValidationErrorsException("providerId, authority and subject are required");
        }
        ExternalIdentityKey key;
        try {
            key = new ExternalIdentityKey(providerId, authority, subject);
        } catch (IllegalArgumentException e) {
            throw new ValidationErrorsException(e.getMessage());
        }
        return get(selectors.identityId(key));
    }

    @PUT
    @Path("/{id}/roles")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateUserRolesById")
    public GenericOperationResult updateUserRolesById(@PathParam("id") UUID id, @javax.validation.Valid UpdateUserRolesRequest request) {
        assertAdmin();
        if (userDao.get(id) == null) {
            throw new ConcordApplicationException("User not found", Response.Status.NOT_FOUND);
        }
        if (request == null || request.getRoles() == null) {
            throw new ValidationErrorsException("Missing roles");
        }
        userDao.updateRoles(id, request.getRoles());
        return new GenericOperationResult(OperationResult.UPDATED);
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Get an existing user", operationId = "getUser")
    public UserEntry get(@PathParam("id") UUID id) {

        UUID authenticatedId = PrincipalNormalization.assertCurrent().userId();

        if (authenticatedId.equals(id) || Roles.isAdmin() || Roles.isGlobalReader()) {
            return project(userDao.get(id));
        }

        throw new UnauthorizedException("Users can only view their own information or must have admin privileges.");
    }

    private UserEntry project(UserEntry user) {
        if (user == null) return null;
        String provider = identityDao.profile(user.getId()).map(IdentityDao.ProfileSelection::providerId).orElse(null);
        var providers = identityDao.list(user.getId()).stream().filter(IdentityRecord::active)
                .map(i -> i.identity().providerId()).collect(java.util.stream.Collectors.toSet());
        user.setProjection(provider, providers, selectors.legacyEnabled());
        return user;
    }

    @GET
    @Path("/{id}/identities")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listUserIdentities")
    public List<IdentityRecord> identities(@PathParam("id") UUID id) {
        assertCanRead(id);
        return identityDao.list(id);
    }

    @POST
    @Path("/{id}/identities")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "linkUserIdentity")
    public IdentityRecord linkIdentity(@PathParam("id") UUID id, ExternalIdentityKey identity) {
        assertAdmin();
        return identityDao.link(id, identity);
    }

    @DELETE
    @Path("/{id}/identities/{identityId}")
    @Operation(operationId = "unlinkUserIdentity")
    public void unlinkIdentity(@PathParam("id") UUID id, @PathParam("identityId") UUID identityId) {
        assertAdmin();
        identityDao.unlink(id, identityId);
    }

    public record AuthorizationBindingsRequest(List<AuthorizationService.BindingSpec> sources) {}

    @GET
    @Path("/{id}/authorization")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listUserAuthorization")
    public List<AuthorizationService.Binding> authorization(@PathParam("id") UUID id) {
        assertCanRead(id);
        try {
            return authorization.listBindings(id);
        } catch (NoSuchElementException e) {
            throw new ConcordApplicationException("User not found", Response.Status.NOT_FOUND);
        }
    }

    @PUT
    @Path("/{id}/authorization")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "replaceUserAuthorization")
    public GenericOperationResult authorization(@PathParam("id") UUID id, AuthorizationBindingsRequest request) {
        assertAdmin();
        if (request == null || request.sources() == null) {
            throw new ValidationErrorsException("Missing authorization sources");
        }
        try {
            authorization.replaceBindings(id, request.sources());
        } catch (NoSuchElementException e) {
            throw new ConcordApplicationException("User not found", Response.Status.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            throw new ValidationErrorsException(e.getMessage());
        }
        return new GenericOperationResult(OperationResult.UPDATED);
    }

    @POST
    @Path("/{id}/authorization/{sourceId}/sync")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "syncUserAuthorization")
    public GenericOperationResult syncAuthorization(@PathParam("id") UUID id, @PathParam("sourceId") String sourceId) {
        assertAdmin();
        try {
            authorization.refresh(id, sourceId, true);
        } catch (NoSuchElementException e) {
            throw new ConcordApplicationException("Authorization binding not found", Response.Status.NOT_FOUND);
        } catch (AuthorizationSubjectNotFoundException e) {
            throw new ConcordApplicationException("Authorization subject not found", Response.Status.FORBIDDEN);
        } catch (AuthorizationUnavailableException e) {
            throw new ConcordApplicationException("Authorization source unavailable", Response.Status.SERVICE_UNAVAILABLE);
        }
        return new GenericOperationResult(OperationResult.UPDATED);
    }

    public record ProfileProviderRequest(String providerId, UUID identityId) {}

    @PUT
    @Path("/{id}/profileProvider")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setUserProfileProvider")
    public void setProfileProvider(@PathParam("id") UUID id, ProfileProviderRequest request) {
        assertAdmin();
        identityDao.setProfile(id, request.providerId(), request.identityId());
    }

    private static void assertCanRead(UUID id) {
        if (!PrincipalNormalization.assertCurrent().userId().equals(id) && !Roles.isAdmin() && !Roles.isGlobalReader()) {
            throw new UnauthorizedException("Users can only view their own information or must have admin privileges.");
        }
    }

    private static void assertAdmin() {
        if (!Roles.isAdmin()) {
            throw new UnauthorizedException("Only admins can do that");
        }
    }
}
