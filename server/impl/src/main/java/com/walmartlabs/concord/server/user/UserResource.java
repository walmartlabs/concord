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

import com.walmartlabs.concord.common.validation.ConcordUsername;
import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.security.Roles;
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
import java.util.Set;
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

        UUID id = userDao.getId(username);
        if (id == null) {
            UserEntry e = userManager.create(username, req.getType());
            return new CreateUserResponse(e.getId(), OperationResult.CREATED);
        } else {
            // TODO allow updating of the type?
            return new CreateUserResponse(id, OperationResult.UPDATED);
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
    public UserEntry findByUsername(@PathParam("username") @ConcordUsername @Size(max = UserEntry.MAX_USERNAME_LENGTH) @NotNull String username) {
        assertAdmin();

        UUID id = userDao.getId(username);
        if (id == null) {
            throw new ConcordApplicationException("User not found: " + username, Status.NOT_FOUND);
        }
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
    public GenericOperationResult updateUserRoles(@ApiParam @PathParam("username") @ConcordUsername @Size(max = UserEntry.MAX_USERNAME_LENGTH) String username,
                                                  @ApiParam @Valid UpdateUserRolesRequest req) {
        assertAdmin();

        UUID id = userDao.getId(username);
        if (id == null) {
            throw new ConcordApplicationException("User not found: " + username, Status.NOT_FOUND);
        }

        userDao.updateRoles(id, req.getRoles());
        return new GenericOperationResult(OperationResult.UPDATED);
    }

    private static void assertAdmin() {
        if (!Roles.isAdmin()) {
            throw new UnauthorizedException("Only admins can do that");
        }
    }
}
