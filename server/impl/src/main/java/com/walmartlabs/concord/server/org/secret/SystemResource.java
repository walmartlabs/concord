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

import com.walmartlabs.concord.repository.RepositoryException;
import com.walmartlabs.concord.repository.auth.ActiveAccessToken;
import com.walmartlabs.concord.repository.auth.HttpAuthProvider;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

@Path("/api/v1/system")
@Tag(name = "System")
public class SystemResource implements Resource {

    private final HttpAuthProvider httpAuthProvider;

    @Inject
    public SystemResource(HttpAuthProvider httpAuthProvider) {
        this.httpAuthProvider = httpAuthProvider;
    }

    /**
     * Refresh repositories by their IDs.
     */
    @GET
    @Path("/gitauth")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Retrieves system-provided auth for give repository URI. Requires systemGitAuth permission. ", operationId = "getSystemGitAuth")
    public ActiveAccessToken get(@QueryParam("gitHost") String gitHost,
                                 @QueryParam("repoUri") URI repoUri) {
        assertSystemGitAuthPermission();

        try {
            return httpAuthProvider.getAccessToken(gitHost, repoUri, null)
                    .orElseThrow(() -> new ConcordApplicationException("No system-provided auth found for the given repository URI: " + repoUri, Response.Status.NOT_FOUND));
        } catch (RepositoryException.NotFoundException e) {
            throw new ConcordApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (RepositoryException e) {
            // TODO 500?
            throw new ConcordApplicationException(e.getMessage(), Response.Status.NOT_FOUND);
        }
    }

    private static void assertSystemGitAuthPermission() {
        if (!Permission.SYSTEM_GIT_AUTH.isPermitted()) {
            throw new ForbiddenException("insufficient privileges to access the resource");
        }
    }
}
