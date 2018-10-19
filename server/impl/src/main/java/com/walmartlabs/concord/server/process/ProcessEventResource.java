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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.IsoDateParam;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.event.EventDao;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Named
@Singleton
@Api(value = "Process Events", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/process")
public class ProcessEventResource implements Resource {

    private final EventDao eventDao;
    private final ObjectMapper objectMapper;

    @Inject
    public ProcessEventResource(EventDao eventDao) {
        this.eventDao = eventDao;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Register a process event.
     *
     * @param processInstanceId
     * @param req
     */
    @POST
    @ApiOperation(value = "Register a process event", authorizations = {@Authorization("session_key"), @Authorization("api_key")})
    @Path("/{processInstanceId}/event")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithTimer
    public void event(@ApiParam @PathParam("processInstanceId") UUID processInstanceId,
                      @ApiParam ProcessEventRequest req) {

        String data;
        try {
            // TODO we should be able to capture the event as is, without converting it from JSON and to JSON again
            data = objectMapper.writeValueAsString(req.getData());
        } catch (IOException e) {
            throw new ConcordApplicationException("Error while serializing the event's data: " + e.getMessage(), e);
        }

        eventDao.insert(processInstanceId, req.getEventType(), data);
    }

    /**
     * List process events.
     *
     * @param processInstanceId
     * @return
     */
    @GET
    @ApiOperation(value = "List process events", responseContainer = "list", response = ProcessEventEntry.class)
    @Path("/{processInstanceId}/event")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<ProcessEventEntry> list(@ApiParam @PathParam("processInstanceId") UUID processInstanceId,
                                        @ApiParam @QueryParam("after") IsoDateParam geTimestamp,
                                        @ApiParam @QueryParam("limit") @DefaultValue("-1") int limit) {

        Timestamp ts = null;
        if (geTimestamp != null) {
            ts = Timestamp.from(geTimestamp.getValue().toInstant());
        }
        return eventDao.list(processInstanceId, ts, limit);
    }
}
