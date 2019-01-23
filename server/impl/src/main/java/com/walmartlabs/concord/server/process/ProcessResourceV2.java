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

import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.util.Set;
import java.util.UUID;

@Named
@Singleton
@Api(value = "ProcessV2", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v2/process")
public class ProcessResourceV2 implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessResourceV2.class);

    private final ProcessQueueDao processDao;

    @Inject
    public ProcessResourceV2(ProcessQueueDao processDao) {
        this.processDao = processDao;
    }

    /**
     * Returns a process instance's details.
     */
    @GET
    @ApiOperation("Get a process' details")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public ProcessEntry get(@ApiParam @PathParam("id") UUID instanceId,
                            @ApiParam @QueryParam("include") Set<ProcessDataInclude> processData) {

        PartialProcessKey processKey = PartialProcessKey.from(instanceId);

        ProcessEntry e = processDao.get(processKey, processData);
        if (e == null) {
            log.warn("get ['{}'] -> not found", instanceId);
            throw new ConcordApplicationException("Process instance not found", Status.NOT_FOUND);
        }
        return e;
    }
}
