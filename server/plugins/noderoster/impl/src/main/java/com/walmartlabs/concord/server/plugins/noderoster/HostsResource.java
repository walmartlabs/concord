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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Named
@Singleton
@Path("/api/v1/noderoster/hosts")
@Api(value = "Node Roster Hosts", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
public class HostsResource implements Resource {

    private static final String DEFAULT_LIMIT = "30";
    private static final String DEFAULT_OFFSET = "0";

    private final HostManager hostManager;
    private final HostsDao hostsDao;

    @Inject
    public HostsResource(HostManager hostManager, HostsDao hostsDao) {
        this.hostManager = hostManager;
        this.hostsDao = hostsDao;
    }

    @GET
    @Path("/")
    @ApiOperation(value = "Get hosts with a deployed artifact")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, List<HostEntry>> deployedOnHosts(@ApiParam @QueryParam("artifactPattern") String artifactPattern,
                                                        @ApiParam @QueryParam("limit") @DefaultValue(DEFAULT_LIMIT) int limit,
                                                        @ApiParam @QueryParam("offset") @DefaultValue(DEFAULT_OFFSET) int offset) {

        assertLimitAndOffset(limit, offset);

        // TODO optimize, do a batch lookup or a join
        List<String> matchingArtifacts = hostsDao.getMatchingArtifacts(artifactPattern, limit, offset);
        return matchingArtifacts.stream()
                .collect(Collectors.toMap(Function.identity(), hostsDao::getHosts));
    }

    @GET
    @Path("/all") // TODO swap with @Path("/")
    @ApiOperation(value = "Get all known hosts", responseContainer = "list", response = HostEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    public List<HostEntry> getAllKnownHosts(@ApiParam @QueryParam("limit") @DefaultValue(DEFAULT_LIMIT) int limit,
                                            @ApiParam @QueryParam("offset") @DefaultValue(DEFAULT_OFFSET) int offset) {

        // TODO add optional filter parameter
        assertLimitAndOffset(limit, offset);
        return hostsDao.getAllKnownHosts(limit, offset);
    }

    @GET
    @Path("/touched")
    @ApiOperation(value = "List hosts touched by the specified project", responseContainer = "list", response = HostEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    public List<HostEntry> touchedHosts(@ApiParam @QueryParam("projectId") UUID projectId,
                                        @ApiParam @QueryParam("limit") @DefaultValue(DEFAULT_LIMIT) int limit,
                                        @ApiParam @QueryParam("offset") @DefaultValue(DEFAULT_OFFSET) int offset) {

        assertLimitAndOffset(limit, offset);
        return hostsDao.findTouchedHosts(projectId, limit, offset);
    }

    @GET
    @Path("/lastInitiator")
    @ApiOperation(value = "Get last initiator of the process that deployed on the specified host")
    @Produces(MediaType.APPLICATION_JSON)
    public InitiatorEntry getLastInitiator(@ApiParam @QueryParam("hostName") String hostName,
                                           @ApiParam @QueryParam("hostId") UUID hostId) {

        UUID effectiveHostId = Utils.getHostId(hostManager, hostId, hostName);
        if (effectiveHostId == null) {
            return null;
        }

        return hostsDao.getLastInitiator(effectiveHostId);
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
