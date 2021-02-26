package com.walmartlabs.concord.server.user;

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

import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
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
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.util.UUID;

@Named
@Singleton
@Api(value = "Users", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/user")
public class UserResource implements Resource {

    private final UserManager userManager;
    private final UserDao userDao;

    @Inject
    public UserResource(UserManager userManager, UserDao userDao) {
        this.userManager = userManager;
        this.userDao = userDao;
    }

    /**
     * Creates a new user or updated an existing one.
     *
     * @param req user's data
     * @return
     */
    @POST
    @ApiOperation("Create a new user or update an existing one")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public CreateUserResponse createOrUpdate(@ApiParam @Valid CreateUserRequest req) {
        assertAdmin();

        String username = req.getUsername();
        UserType type = assertUserType(req.getType());

        UUID id = userManager.getId(username, req.getUserDomain(), type).orElse(null);
        if (id == null) {
            UserEntry e = userManager.create(username, req.getUserDomain(), req.getDisplayName(), req.getEmail(), req.getType(), req.getRoles());
            return new CreateUserResponse(e.getId(), e.getName(), OperationResult.CREATED);
        } else {
            UserEntry e = userManager.update(id, req.getDisplayName(), req.getEmail(), req.getType(), req.isDisabled(), req.getRoles()).orElse(null);
            if (e == null) {
                throw new ConcordApplicationException("User not found: " + id, Status.BAD_REQUEST);
            }
            return new CreateUserResponse(id, e.getName(), OperationResult.UPDATED);
        }
    }

    /**
     * Finds an existing user by username.
     *
     * @param username
     * @return
     */
    @GET
    @ApiOperation("Find a user")
    @Path("/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public UserEntry findByUsername(@PathParam("username") @Size(max = UserEntry.MAX_USERNAME_LENGTH) @NotNull String username) {
        assertAdmin();

        UUID id = userManager.getId(username, null, null)
                .orElseThrow(() -> new ConcordApplicationException("User not found: " + username, Status.NOT_FOUND));

        return userDao.get(id);
    }

    /**
     * Removes an existing user.
     *
     * @param id
     * @return
     */
    @DELETE
    @ApiOperation("Delete an existing user")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    // TODO use usernames instead of IDs?
    public DeleteUserResponse delete(@ApiParam @PathParam("id") UUID id) {
        assertAdmin();

        if (!userDao.existsById(id)) {
            throw new ValidationErrorsException("User not found: " + id);
        }

        userDao.delete(id);
        return new DeleteUserResponse();
    }

    @POST
    @ApiOperation("Update the list of roles for the existing user")
    @Path("/{username}/roles")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public GenericOperationResult updateUserRoles(@ApiParam @PathParam("username") @Size(max = UserEntry.MAX_USERNAME_LENGTH) String username,
                                                  @ApiParam @Valid UpdateUserRolesRequest req) {
        assertAdmin();

        // TODO: type from request
        UserType type = UserPrincipal.assertCurrent().getType();
        // TODO: userDomain from request
        String userDomain = null;
        UUID id = userManager.getId(username, userDomain, type)
                .orElseThrow(() -> new ConcordApplicationException("User not found: " + username, Status.NOT_FOUND));

        userDao.updateRoles(id, req.getRoles());
        return new GenericOperationResult(OperationResult.UPDATED);
    }

    private static void assertAdmin() {
        if (!Roles.isAdmin()) {
            throw new UnauthorizedException("Only admins can do that");
        }
    }

    private static UserType assertUserType(UserType type) {
        if (type != null && type.equals(UserType.SSO)) {
            throw new ConcordApplicationException("User of type "+type.name()+" cannot be created", Status.BAD_REQUEST);
        }
        
        if (type != null) {
            return type;
        }
        return UserPrincipal.assertCurrent().getType();
    }
}
