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

import com.walmartlabs.concord.server.OffsetDateTimeParam;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.process.queue.ProcessKeyCache;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao.ProjectIdAndInitiator;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.Utils.unwrap;

@Named
@Singleton
@Path("/api/v1/process")
@Tag(name = "Process Events")
public class ProcessEventResource implements Resource {

    // TODO this should be a common constant (in SDK maybe)
    private static final String ELEMENT_EVENT_TYPE = "ELEMENT";

    private final ProcessKeyCache processKeyCache;
    private final ProcessQueueDao queueDao;
    private final ProjectAccessManager projectAccessManager;
    private final ProcessEventManager eventManager;

    @Inject
    public ProcessEventResource(ProcessKeyCache processKeyCache,
                                ProcessQueueDao queueDao,
                                ProjectAccessManager projectAccessManager,
                                ProcessEventManager eventManager) {

        this.processKeyCache = processKeyCache;
        this.queueDao = queueDao;
        this.projectAccessManager = projectAccessManager;
        this.eventManager = eventManager;
    }

    /**
     * Register a process event.
     */
    @POST
    @Path("/{processInstanceId}/event")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Register a process event")
    public void event(@Parameter @PathParam("processInstanceId") UUID processInstanceId,
                      @Parameter ProcessEventRequest req) {

        ProcessKey processKey = assertProcessKey(processInstanceId);
        NewProcessEvent e = NewProcessEvent.builder()
                .processKey(processKey)
                .eventType(req.getEventType())
                .eventDate(req.getEventDate())
                .data(req.getData())
                .build();
        eventManager.event(Collections.singletonList(e));
    }

    /**
     * Register multiple events for the specified process.
     */
    @POST
    @Path("/{processInstanceId}/eventBatch")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Register multiple events for the specified process")
    public void batchEvent(@Parameter @PathParam("processInstanceId") UUID processInstanceId,
                           @Parameter List<ProcessEventRequest> data) {

        ProcessKey processKey = assertProcessKey(processInstanceId);

        List<NewProcessEvent> events = data.stream()
                .map(req -> NewProcessEvent.builder()
                        .processKey(processKey)
                        .eventType(req.getEventType())
                        .eventDate(req.getEventDate())
                        .data(req.getData())
                        .build())
                .collect(Collectors.toList());

        eventManager.event(events);
    }

    /**
     * List process events.
     */
    @GET
    @Path("/{processInstanceId}/event")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "List process events", operationId = "listProcessEvents")
    public List<ProcessEventEntry> list(@Parameter @PathParam("processInstanceId") UUID processInstanceId,
                                        @Parameter @QueryParam("type") String eventType,
                                        @Parameter @QueryParam("after") OffsetDateTimeParam after,
                                        @Parameter @QueryParam("fromId") Long fromId,
                                        @Parameter @QueryParam("eventCorrelationId") UUID eventCorrelationId,
                                        @Parameter @QueryParam("eventPhase") EventPhase eventPhase,
                                        @Parameter @QueryParam("includeAll") @DefaultValue("false") boolean includeAll,
                                        @Parameter @QueryParam("limit") @DefaultValue("-1") int limit) {

        ProcessKey processKey = assertProcessKey(processInstanceId);

        if (includeAll) {
            // verify that the user can access potentially sensitive data
            assertAccessRights(processKey);
        }

        ProcessEventFilter f = ProcessEventFilter.builder()
                .processKey(processKey)
                .after(unwrap(after))
                .eventType(eventType)
                .eventCorrelationId(eventCorrelationId)
                .eventPhase(eventPhase)
                .limit(limit)
                .fromId(fromId)
                .build();

        List<ProcessEventEntry> l = eventManager.list(f);
        if (!includeAll) {
            l = filterOutSensitiveData(l);
        }

        return l;
    }

    private ProcessKey assertProcessKey(UUID instanceId) {
        ProcessKey processKey = processKeyCache.get(instanceId);
        if (processKey == null) {
            throw new ConcordApplicationException("Process instance not found", Response.Status.NOT_FOUND);
        }
        return processKey;
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

        ProjectIdAndInitiator ids = queueDao.getProjectIdAndInitiator(processKey);
        if (ids.getProjectId() != null) {
            // if the process belongs to a project, only those who have WRITER privileges can
            // access extended event data
            if (projectAccessManager.assertAccess(ids.getProjectId(), ResourceAccessLevel.WRITER, true) != null) {
                return;
            }
        }

        if (p.getId().equals(ids.getInitiatorId())) {
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
