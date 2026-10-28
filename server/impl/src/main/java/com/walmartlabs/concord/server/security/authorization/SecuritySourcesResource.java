package com.walmartlabs.concord.server.security.authorization;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.security.GroupRef;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;

import javax.inject.Inject;
import javax.validation.constraints.Size;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;

@Path("/api/v2/security")
public class SecuritySourcesResource implements Resource {
    public record Source(String sourceId, boolean active) {}
    private final AuthorizationService authorization;

    @Inject
    public SecuritySourcesResource(AuthorizationService authorization) {
        this.authorization = authorization;
    }

    @GET
    @Path("/sources")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listAuthorizationSources")
    public List<Source> sources() {
        UserPrincipal.assertCurrent();
        return authorization.sources().entrySet().stream().map(e -> new Source(e.getKey(), e.getValue())).toList();
    }

    @GET
    @Path("/groups")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(operationId = "searchAuthorizationGroups")
    public Set<GroupRef> groups(@QueryParam("sourceId") String sourceId,
                                @QueryParam("filter") @Size(min = 5, max = 256) String filter) {
        UserPrincipal.assertCurrent();
        if (sourceId == null || !authorization.sources().containsKey(sourceId)) {
            throw new ValidationErrorsException("Unknown authorization source: " + sourceId);
        }
        if (filter == null || filter.trim().startsWith("*")) return Set.of();
        return authorization.searchGroups(sourceId, filter.trim());
    }
}
