package com.walmartlabs.concord.server.agent;

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

import com.walmartlabs.concord.server.AgentWorkerUtils;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

@Path("/api/v1/agent")
@Tag(name = "Agents")
public class AgentResource implements Resource {

    private final AgentManager agentManager;

    @Inject
    public AgentResource(AgentManager agentManager) {
        this.agentManager = agentManager;
    }

    @GET
    @Path("/all/workers")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "List currently available agent workers")
    public Collection<AgentWorkerEntry> listWorkers() {
        return agentManager.getAvailableAgents();
    }

    @GET
    @Path("/all/workersCount")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Counts the currently connected workers based on the specified capabilities property")
    public Map<Object, Long> aggregate(@QueryParam("capabilities") String capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            throw new ValidationErrorsException("'capabilities' filter is required");
        }

        Collection<AgentWorkerEntry> data = agentManager.getAvailableAgents();
        if (data == null || data.isEmpty()) {
            return Collections.emptyMap();
        }

        String[] path = capabilities.split("\\.");
        if (path.length < 1 || Stream.of(path).anyMatch(p -> p.trim().isEmpty())) {
            throw new ValidationErrorsException("Invalid 'capabilities' value. Expected a path to a property, got: " + capabilities);
        }

        return AgentWorkerUtils.groupBy(data, path);
    }
}
