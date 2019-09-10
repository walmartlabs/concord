package com.walmartlabs.concord.server.process.event;

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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.walmartlabs.concord.server.IsoDateParam;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.process.PartialProcessKey;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.queue.ProcessKeyCache;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.sql.Timestamp;
import java.util.*;

@Named
@Singleton
@Api(value = "Process Events", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/process")
public class ProcessEventResource implements Resource {

    // TODO this should be a common constant (in SDK maybe)
    private static final String ELEMENT_EVENT_TYPE = "ELEMENT";

    private final ProcessEventDao eventDao;
    private final ProcessKeyCache processKeyCache;
    private final ProcessQueueDao queueDao;
    private final ProjectAccessManager projectAccessManager;

    private final Histogram batchInsertHistogram;

    @Inject
    public ProcessEventResource(ProcessEventDao eventDao,
                                ProcessKeyCache processKeyCache,
                                ProcessQueueDao queueDao,
                                ProjectAccessManager projectAccessManager,
                                MetricRegistry metricRegistry) {

        this.eventDao = eventDao;
        this.processKeyCache = processKeyCache;
        this.queueDao = queueDao;
        this.projectAccessManager = projectAccessManager;
        this.batchInsertHistogram = metricRegistry.histogram("process-events-batch-insert");
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

        ProcessKey processKey = processKeyCache.get(processInstanceId);
        eventDao.insert(processKey, req.getEventType(), req.getEventDate(), req.getData());
    }

    /**
     * Register multiple events for the specified process.
     *
     * @param processInstanceId
     * @param data
     */
    @POST
    @ApiOperation(value = "Register multiple events for the specified process", authorizations = {@Authorization("session_key"), @Authorization("api_key")})
    @Path("/{processInstanceId}/eventBatch")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithTimer
    public void batchEvent(@ApiParam @PathParam("processInstanceId") UUID processInstanceId,
                           @ApiParam List<ProcessEventRequest> data) {

        ProcessKey processKey = processKeyCache.get(processInstanceId);
        eventDao.insert(processKey, data);

        batchInsertHistogram.update(data.size());
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
                                        @ApiParam @QueryParam("type") String eventType,
                                        @ApiParam @QueryParam("after") IsoDateParam geTimestamp,
                                        @ApiParam @QueryParam("fromId") Long fromId,
                                        @ApiParam @QueryParam("eventCorrelationId") UUID eventCorrelationId,
                                        @ApiParam @QueryParam("eventPhase") EventPhase eventPhase, // TODO make it case-insensitive?
                                        @ApiParam @QueryParam("includeAll") @DefaultValue("false") boolean includeAll,
                                        @ApiParam @QueryParam("limit") @DefaultValue("-1") int limit) {

        ProcessKey processKey = processKeyCache.get(processInstanceId);

        if (includeAll) {
            // verify that the user can access potentially sensitive data
            assertAccessRights(processKey);
        }

        Timestamp ts = null;
        if (geTimestamp != null) {
            ts = Timestamp.from(geTimestamp.getValue().toInstant());
        }

        ProcessEventFilter f = ProcessEventFilter.builder()
                .processKey(processKey)
                .after(ts)
                .eventType(eventType)
                .eventCorrelationId(eventCorrelationId)
                .eventPhase(eventPhase)
                .limit(limit)
                .fromId(fromId)
                .build();

        List<ProcessEventEntry> l = eventDao.list(f);
        if (!includeAll) {
            l = filterOutSensitiveData(l);
        }

        return l;
    }

    private void assertAccessRights(PartialProcessKey processKey) {
        if (Roles.isAdmin()) {
            // an admin can access any project
            return;
        }

        UserPrincipal p = UserPrincipal.getCurrent();
        if (p == null) {
            return;
        }

        // TODO fetch both initiatorId and projectId simultaneously?
        UUID projectId = queueDao.getProjectId(processKey);
        if (projectId != null) {
            // if the process belongs to a project, only those who have WRITER privileges can
            // access extended event data
            projectAccessManager.assertAccess(projectId, ResourceAccessLevel.WRITER, true);
        }

        UUID initiatorId = queueDao.getInitiatorId(processKey);
        if (p.getId().equals(initiatorId)) {
            // if it is a standalone process, only the initator can access extended event data
            return;
        }

        throw new UnauthorizedException("Only admins, process initiators and those who have READER access to " +
                "the process' projects can access the extended process event data");
    }

    private static List<ProcessEventEntry> filterOutSensitiveData(List<ProcessEventEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return entries;
        }

        List<ProcessEventEntry> result = new ArrayList<>(entries.size());
        for (ProcessEventEntry e : entries) {
            if (!ELEMENT_EVENT_TYPE.equals(e.eventType())) {
                result.add(e);
                continue;
            }

            Map<String, Object> data = e.data();
            if (data == null) {
                result.add(e);
                continue;
            }

            // remove in/out variables
            Map<String, Object> m = new HashMap<>(data);
            m.remove("in");
            m.remove("out");

            result.add(ProcessEventEntry.from(e)
                    .data(m)
                    .build());
        }

        return result;
    }
}
