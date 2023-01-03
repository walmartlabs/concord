package com.walmartlabs.concord.server.user;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Named
@Singleton
@Api(value = "UserV2", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v2/user")
public class UserResourceV2 implements Resource {

    private final UserDao userDao;

    @Inject
    public UserResourceV2(UserDao userDao) {
        this.userDao = userDao;
    }

    @GET
    @ApiOperation("Returns a list of existing active users matching the supplied filter")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<UserEntry> list(@ApiParam @QueryParam("offset") @DefaultValue("0") int offset,
                                @ApiParam @QueryParam("limit") @DefaultValue("10") int limit,
                                @ApiParam @QueryParam("filter") String filter) {

        if (offset < 0) {
            throw new ValidationErrorsException("'offset' must be a positive number or zero");
        }

        if (limit < 1) {
            throw new ValidationErrorsException("'limit' must be a positive number");
        }

        return userDao.list(filter, offset, limit);
    }

    @GET
    @ApiOperation("Get an existing user")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public UserEntry get(@PathParam("id") UUID id) {
        return userDao.get(id);
    }
}
