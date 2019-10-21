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

import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Collection;

@Named
@Singleton
@Api(value = "Agents", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/agent")
public class AgentResource implements Resource {

    private final AgentManager agentManager;

    @Inject
    public AgentResource(AgentManager agentManager) {
        this.agentManager = agentManager;
    }

    @GET
    @ApiOperation(value = "List currently available agent workers", responseContainer = "list", response = AgentWorkerEntry.class)
    @Path("/all/workers")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public Collection<AgentWorkerEntry> listWorkers() {
        return agentManager.getAvailableAgents();
    }
}
