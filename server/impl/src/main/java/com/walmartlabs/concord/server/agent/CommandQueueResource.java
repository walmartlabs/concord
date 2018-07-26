package com.walmartlabs.concord.server.agent;

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

import com.walmartlabs.concord.server.CommandType;
import com.walmartlabs.concord.server.metrics.WithTimer;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Named
@Singleton
@Api(value = "CommandQueue", authorizations = {@Authorization("api_key")})
@Path("/api/v1/command/queue")
public class CommandQueueResource implements Resource {

    private final AgentCommandsDao commandsDao;

    @Inject
    public CommandQueueResource(AgentCommandsDao commandsDao) {
        this.commandsDao = commandsDao;
    }

    @GET
    @ApiOperation(value = "Take command from queue")
    @Path("/take/{agentId}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public CommandEntry take(@PathParam("agentId") String agentId) {
        AgentCommand c = commandsDao.poll(agentId).orElse(null);
        if (c == null) {
            return null;
        }

        CommandType type = CommandType.valueOf((String) c.getData().remove(Commands.TYPE_KEY));

        return new CommandEntry(type, c.getData());
    }
}
