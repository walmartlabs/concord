package com.walmartlabs.concord.server.org.secret;

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

import com.walmartlabs.concord.server.repository.HttpAuthProviderImpl;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.net.URI;

@Path("/api/v1/secret/gitauth")
@Tag(name = "GitAuth")
public class GitAuthResource implements Resource {

    private final HttpAuthProviderImpl httpAuthProviderImpl;

    @Inject
    public GitAuthResource(HttpAuthProviderImpl httpAuthProviderImpl) {
        this.httpAuthProviderImpl = httpAuthProviderImpl;
    }

    /**
     * Refresh repositories by their IDs.
     */
    @GET
    @Path("/token/{gitHost}/{repoUri}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Retrieves git token based on system auth configuration", operationId = "tokenGitAuth")
    public String get(@PathParam("gitHost") String gitHost,
                      @PathParam("repoUri") URI repoUri) {
        assertAdmin();

        return httpAuthProviderImpl.get(gitHost, repoUri, null);
    }

    private static void assertAdmin() {
        if (!Roles.isAdmin()) {
            throw new UnauthorizedException("Not authorized, admin access required");
        }
    }
}
