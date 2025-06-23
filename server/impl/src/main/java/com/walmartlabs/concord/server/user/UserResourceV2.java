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
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/api/v2/user")
@Tag(name = "UserV2")
public class UserResourceV2 implements Resource {

    private final UserDao userDao;

    @Inject
    public UserResourceV2(UserDao userDao) {
        this.userDao = userDao;
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

        return userDao.list(filter, offset, limit);
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Get an existing user", operationId = "getUser")
    public UserEntry get(@PathParam("id") UUID id) {

        UserPrincipal loggedIn = UserPrincipal.assertCurrent();

        UUID authenticatedId = loggedIn.getId();

        if (authenticatedId.equals(id) || Roles.isAdmin() || Roles.isGlobalReader()) {
            return userDao.get(id);
        }

        throw new UnauthorizedException("Users can only view their own information or must have admin privileges.");
    }
}
