package com.walmartlabs.concord.server.events;

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

import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.cfg.ExternalEventsConfiguration;
import com.walmartlabs.concord.server.events.externalevent.ExternalEventTriggerProcessor;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.user.UserManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

import static com.walmartlabs.concord.common.MemoSupplier.memo;

/**
 * Handles generic external events.
 * Receives arbitrary JSON bodies and matches them with whatever is configured
 * in the trigger.
 */
@Named
@Singleton
@Path("/api/v1/events")
@Tag(name = "External Events")
public class ExternalEventResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ExternalEventResource.class);

    private final ExternalEventsConfiguration cfg;
    private final TriggerProcessExecutor executor;
    private final UserManager userManager;
    private final TriggerEventInitiatorResolver initiatorResolver;
    private final List<ExternalEventTriggerProcessor> processors;
    private final AuditLog auditLog;

    @Inject
    public ExternalEventResource(ExternalEventsConfiguration cfg,
                                 TriggerProcessExecutor executor,
                                 UserManager userManager,
                                 TriggerEventInitiatorResolver initiatorResolver,
                                 List<ExternalEventTriggerProcessor> processors,
                                 AuditLog auditLog) {

        this.cfg = cfg;
        this.executor = executor;
        this.userManager = userManager;
        this.initiatorResolver = initiatorResolver;
        this.processors = processors;
        this.auditLog = auditLog;
    }

    @POST
    @Path("/{eventName:.*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Handles an external event", operationId = "externalEvent")
    public Response event(@PathParam("eventName") String eventName,
                          Map<String, Object> data) {

        if (executor.isDisabled(eventName)) {
            log.warn("event ['{}'] disabled", eventName);
            return Response.ok().build();
        }

        Map<String, Object> event = data != null ? data : new HashMap<>();

        String eventId = event.computeIfAbsent("id", s -> UUID.randomUUID()).toString();

        if (cfg.isLogEvents()) {
            auditLog.add(AuditObject.EXTERNAL_EVENT, AuditAction.ACCESS)
                    .field("source", eventName)
                    .field("eventId", eventId)
                    .field("payload", event)
                    .log();
        }

        List<ExternalEventTriggerProcessor.Result> results = new ArrayList<>();
        processors.forEach(p -> p.process(eventName, event, results));

        for (ExternalEventTriggerProcessor.Result r : results) {
            Event e = Event.builder()
                    .id(eventId)
                    .name(eventName)
                    .attributes(r.event())
                    .initiator(memo(new EventInitiatorSupplier("author", userManager, r.event())))
                    .build();

            List<PartialProcessKey> processKeys = executor.execute(e, initiatorResolver, r.triggers());
            log.info("event ['{}', '{}', '{}'] -> done, {} processes started", eventId, eventName, event, processKeys.size());
        }

        return Response.ok().build();
    }
}
