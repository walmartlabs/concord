package com.walmartlabs.concord.server.plugins.noderoster;

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

import com.walmartlabs.concord.server.plugins.noderoster.dao.HostsDao;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Named
@Singleton
@Path("/api/v1/noderoster/processes")
@Api(value = "Node Roster Processes", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
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
    @ApiOperation(value = "Get all known hosts", responseContainer = "list", response = ProcessEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProcessEntry> list(@ApiParam @QueryParam("hostId") UUID hostId,
                                   @ApiParam @QueryParam("hostName") String hostName,
                                   @ApiParam @QueryParam("limit") @DefaultValue(DEFAULT_LIMIT) int limit,
                                   @ApiParam @QueryParam("offset") @DefaultValue(DEFAULT_OFFSET) int offset) {

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
