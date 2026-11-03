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
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.events.ExternalEventService;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
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
import java.util.List;
import java.util.Map;

/**
 * Handles external OneOps events.
 * At the moment supports only VM replacement events. Other event types might
 * require additional support code in {@link OneOpsEventMapper}.
 */
@Named
@Singleton
@Path("/api/v1/events")
public class OneOpsEventResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(OneOpsEventResource.class);

    private static final String EVENT_SOURCE = "oneops";

    private final ObjectMapper objectMapper;
    private final ExternalEventService eventService;
    private final OneOpsConfiguration cfg;

    @Inject
    public OneOpsEventResource(ObjectMapper objectMapper,
                               ExternalEventService eventService,
                               OneOpsConfiguration cfg) {
        this.objectMapper = objectMapper;
        this.eventService = eventService;
        this.cfg = cfg;
    }

    @POST
    @Path("/oneops")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithTimer
    public Response event(Map<String, Object> event) {
        if (eventService.isDisabled(EVENT_SOURCE)) {
            log.warn("event ['{}'] disabled", EVENT_SOURCE);
            return Response.ok().build();
        }

        if (event == null || event.isEmpty()) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        String eventId = String.valueOf(event.get("cmsId"));
        if (cfg.isLogEvents()) {
            eventService.audit(EVENT_SOURCE, eventId, event);
        }

        int processCount = eventService.submit(List.of(
                OneOpsEventMapper.map(eventId, event, 1),
                OneOpsEventMapper.map(eventId, event, 2))).size();
        log.info("event ['{}'] -> done, {} processes started", eventId, processCount);

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
