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

import com.walmartlabs.concord.server.plugins.noderoster.dao.ArtifactsDao;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Named
@Singleton
@Path("/api/v1/noderoster/artifacts")
@Api(value = "Node Roster Artifacts", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
public class ArtifactsResource implements Resource {

    private final HostManager hosts;
    private final ArtifactsDao artifactsDao;

    @Inject
    public ArtifactsResource(HostManager hosts, ArtifactsDao artifactsDao) {
        this.hosts = hosts;
        this.artifactsDao = artifactsDao;
    }

    @GET
    @Path("/")
    @ApiOperation(value = "List artifacts deployed on a host", responseContainer = "list", response = ArtifactEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    public List<ArtifactEntry> deployedArtifacts(@ApiParam @QueryParam("hostName") String hostName,
                                                 @ApiParam @QueryParam("hostId") UUID hostId) {

        if (hostName == null && hostId == null) {
            throw new ValidationErrorsException("A 'hostName' or 'hostId' value is required");
        }

        UUID id = hostId;
        if (id == null) {
            id = hosts.getId(hostName);
        }

        if (id == null) {
            return Collections.emptyList();
        }

        return artifactsDao.getArtifacts(id);
    }
}
