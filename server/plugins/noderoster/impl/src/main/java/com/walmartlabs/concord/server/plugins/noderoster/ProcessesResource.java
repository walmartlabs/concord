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
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Named
@Singleton
@Path("/api/v1/noderoster/processes")
@Tag(name = "Node Roster Processes")
public class ProcessesResource implements Resource {

    private static final String DEFAULT_LIMIT = "30";
    private static final String DEFAULT_OFFSET = "0";

    private final HostManager hosts;
    private final HostsDao hostsDao;

    @Inject
    public ProcessesResource(HostManager hosts, HostsDao hostsDao) {
        this.hosts = hosts;
        this.hostsDao = hostsDao;
    }

    @GET
    @Path("/")
    @Operation(description = "Get all known hosts", operationId = "listHosts")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProcessEntry> list(@QueryParam("hostId") UUID hostId,
                                   @QueryParam("hostName") String hostName,
                                   @QueryParam("limit") @DefaultValue(DEFAULT_LIMIT) int limit,
                                   @QueryParam("offset") @DefaultValue(DEFAULT_OFFSET) int offset) {

        if (hostName == null && hostId == null) {
            throw new ValidationErrorsException("A 'hostName' or 'hostId' value is required");
        }

        assertLimitAndOffset(limit, offset);

        UUID effectiveHostId = hosts.getId(hostId, hostName);

        if (effectiveHostId == null) {
            return Collections.emptyList();
        }

        return hostsDao.listProcesses(effectiveHostId, limit, offset);
    }

    private static void assertLimitAndOffset(int limit, int offset) {
        if (limit <= 0) {
            throw new ValidationErrorsException("'limit' must be a positive number");
        }

        if (offset < 0) {
            throw new ValidationErrorsException("'offset' must be equal or more than zero");
        }
    }
}