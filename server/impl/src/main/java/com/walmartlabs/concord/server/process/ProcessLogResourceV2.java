package com.walmartlabs.concord.server.process;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.server.IsoDateParam;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao;
import com.walmartlabs.concord.server.process.queue.MetadataUtils;
import com.walmartlabs.concord.server.process.queue.ProcessFilter;
import com.walmartlabs.concord.server.process.queue.ProcessFilter.MetadataFilter;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.Permission;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.sql.Timestamp;
import java.util.*;

@Named
@Singleton
@Api(value = "ProcessV2", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v2/process")
public class ProcessLogResourceV2 implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessLogResourceV2.class);

    private final ProcessLogsDao logsDao;

    @Inject
    public ProcessLogResourceV2(ProcessLogsDao logsDao) {
        this.logsDao = logsDao;
    }

    /**
     * List process log segments.
     */
    @GET
    @ApiOperation(value = "List process log segments")
    @Path("{id}/log/segment")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<LogSegment> segments(@ApiParam @PathParam("id") UUID instanceId,
                                     @ApiParam @QueryParam("limit") @DefaultValue("30") int limit,
                                     @ApiParam @QueryParam("offset") @DefaultValue("0") int offset) {

        if (limit <= 0) {
            throw new ValidationErrorsException("'limit' must be a positive number");
        }

        if (offset < 0) {
            throw new ValidationErrorsException("'offset' must be a positive number or zero");
        }

        ProcessKey processKey = assertLogAccess(instanceId);

        return logsDao.listSegments(instanceId, )
    }

    public enum LogSegmentStatus {
        OK,
        FAILED,
        RUNNING
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableLogSegment.class)
    @JsonDeserialize(as = ImmutableLogSegment.class)
    interface LogSegment {

        UUID correlationId();

        String name();

        LogSegmentStatus status();
    }
}
