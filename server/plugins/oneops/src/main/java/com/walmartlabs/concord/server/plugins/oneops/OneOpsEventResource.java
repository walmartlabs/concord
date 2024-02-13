package com.walmartlabs.concord.server.plugins.oneops;

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
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.events.Event;
import com.walmartlabs.concord.server.events.EventInitiatorSupplier;
import com.walmartlabs.concord.server.events.TriggerEventInitiatorResolver;
import com.walmartlabs.concord.server.events.TriggerProcessExecutor;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.user.UserManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.common.MemoSupplier.memo;

/**
 * Handles external OneOps events.
 * At the moment supports only VM replacement events. Other event types might
 * require additional support code in {@link OneOpsTriggerProcessor}.
 */
@Named
@Singleton
@Path("/api/v1/events")
public class OneOpsEventResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(OneOpsEventResource.class);

    private static final String EVENT_SOURCE = "oneops";

    private final ObjectMapper objectMapper;
    private final TriggerProcessExecutor executor;
    private final UserManager userManager;
    private final TriggerEventInitiatorResolver initiatorResolver;
    private final List<OneOpsTriggerProcessor> processors;
    private final AuditLog auditLog;
    private final OneOpsConfiguration cfg;

    @Inject
    public OneOpsEventResource(ObjectMapper objectMapper,
                               TriggerProcessExecutor executor,
                               UserManager userManager,
                               TriggerEventInitiatorResolver initiatorResolver,
                               List<OneOpsTriggerProcessor> processors,
                               AuditLog auditLog,
                               OneOpsConfiguration cfg) {
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.userManager = userManager;
        this.initiatorResolver = initiatorResolver;
        this.processors = processors;
        this.auditLog = auditLog;
        this.cfg = cfg;
    }

    @POST
    @Path("/oneops")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithTimer
    public Response event(Map<String, Object> event) {
        if (executor.isDisabled(EVENT_SOURCE)) {
            log.warn("event ['{}'] disabled", EVENT_SOURCE);
            return Response.ok().build();
        }

        if (event == null || event.isEmpty()) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        if (cfg.isLogEvents()) {
            auditLog.add(AuditObject.EXTERNAL_EVENT, AuditAction.ACCESS)
                    .field("source", EVENT_SOURCE)
                    .field("eventId", String.valueOf(event.get("cmsId")))
                    .field("payload", event)
                    .log();
        }

        List<OneOpsTriggerProcessor.Result> results = new ArrayList<>();
        processors.forEach(p -> p.process(event, results));

        for (OneOpsTriggerProcessor.Result result : results) {
            Event e = Event.builder()
                    .id(String.valueOf(event.get("cmsId")))
                    .name(EVENT_SOURCE)
                    .attributes(result.event())
                    // "author" property is set by OneOpsTriggerProcessor
                    // TODO replace with a custom Supplier<UserEntry> that directly looks at the event's "createdBy"
                    .initiator(memo(new EventInitiatorSupplier("author", userManager, result.event())))
                    .build();

            List<PartialProcessKey> processKeys = executor.execute(e, initiatorResolver, result.triggers());
            log.info("event ['{}'] -> done, {} processes started", e.id(), processKeys.size());
        }

        return Response.ok().build();
    }

    @POST
    @Path("/oneops")
    @Consumes(MediaType.WILDCARD)
    @WithTimer
    @SuppressWarnings("unchecked")
    public Response event(InputStream in) {
        Map<String, Object> m;
        try {
            m = objectMapper.readValue(in, Map.class);
        } catch (IOException e) {
            throw new ConcordApplicationException("Error while reading JSON data: " + e.getMessage(), e);
        }

        return event(m);
    }
}
