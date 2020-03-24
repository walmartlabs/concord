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

import com.walmartlabs.concord.server.cfg.ExternalEventsConfiguration;
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.events.externalevent.ExternalEventTriggerProcessor;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.process.PartialProcessKey;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.process.ProcessSecurityContext;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.user.UserManager;
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
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * Handles generic external events.
 * Receives arbitrary JSON bodies and matches them with whatever is configured
 * in the trigger.
 */
@Named
@Singleton
@Api(value = "External Events", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/events")
public class ExternalEventResource extends AbstractEventResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ExternalEventResource.class);

    private final List<ExternalEventTriggerProcessor> processors;

    @Inject
    public ExternalEventResource(ExternalEventsConfiguration cfg,
                                 ProcessManager processManager,
                                 ProjectDao projectDao,
                                 RepositoryDao repositoryDao,
                                 TriggersConfiguration triggersCfg,
                                 UserManager userManager,
                                 ProcessSecurityContext processSecurityContext,
                                 List<ExternalEventTriggerProcessor> processors) {

        super(cfg, processManager, projectDao, repositoryDao, triggersCfg, userManager, processSecurityContext);
        this.processors = processors;
    }

    @POST
    @ApiOperation("Handles an external event")
    @Path("/{eventName:.*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithTimer
    public Response event(@ApiParam @PathParam("eventName") String eventName,
                          @ApiParam Map<String, Object> data) {

        if (isDisabled(eventName)) {
            log.warn("event ['{}'] disabled", eventName);
            return Response.ok().build();
        }

        Map<String, Object> event = data != null ? data : Collections.emptyMap();

        String eventId = (String) event.computeIfAbsent("id", s -> UUID.randomUUID().toString());

        List<ExternalEventTriggerProcessor.Result> results = new ArrayList<>();
        processors.forEach(p -> p.process(eventName, event, results));

        for (ExternalEventTriggerProcessor.Result r : results) {
            List<PartialProcessKey> processKeys =
                    process(eventId, eventName, r.event(), r.triggers(), null);
            log.info("event ['{}', '{}', '{}'] -> done, {} processes started", eventId, eventName, event, processKeys.size());
        }

        return Response.ok().build();
    }
}