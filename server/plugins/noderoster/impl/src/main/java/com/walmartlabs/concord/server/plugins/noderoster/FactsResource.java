package com.walmartlabs.concord.server.plugins.noderoster;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.server.plugins.noderoster.dao.HostsDao;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Named
@Singleton
@Path("/api/v1/noderoster/facts")
@Tag(name = "Node Roster Facts")
public class FactsResource implements Resource {

    private final HostManager hostManager;
    private final HostsDao hostsDao;

    @Inject
    public FactsResource(HostManager hostManager, HostsDao hostsDao) {
        this.hostManager = hostManager;
        this.hostsDao = hostsDao;
    }

    @GET
    @Path("/last")
    @Operation(description = "Get last known Ansible facts for a host")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponse(description = "Facts content",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = "object"))
    )
    public Response getFacts(@QueryParam("hostId") UUID hostId,
                             @QueryParam("hostName") String hostName) {

        if (hostName == null && hostId == null) {
            throw new ValidationErrorsException("A 'hostName' or 'hostId' value is required");
        }

        UUID effectiveHostId = hostManager.getId(hostId, hostName);
        if (effectiveHostId == null) {
            return Response.ok().entity("{}").build();
        }

        String result = hostsDao.getLastFacts(effectiveHostId);
        if (result == null) {
            return Response.ok().entity("{}").build();
        }

        // return the raw JSON string, no need to parse it just to serialize it back
        return Response.ok()
                .entity(result)
                .build();
    }
}