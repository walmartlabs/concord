package com.walmartlabs.concord.server.process.checkpoint;

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

import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.process.state.ProcessCheckpointManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/api/v3/process")
@Tag(name = "CheckpointV3")
public class ProcessCheckpointV3Resource implements Resource {

    private final ProcessManager processManager;
    private final ProcessCheckpointManager checkpointManager;

    @Inject
    public ProcessCheckpointV3Resource(ProcessManager processManager,
                                       ProcessCheckpointManager checkpointManager) {

        this.processManager = processManager;
        this.checkpointManager = checkpointManager;
    }

    @GET
    @Path("{id}/checkpoint")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "List the process checkpoints with pagination", operationId = "pageCheckpoints")
    public List<ProcessEntry.ProcessCheckpointEntry> page(
            @PathParam("id") UUID instanceId,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("10") int limit
    ) {

        ProcessEntry entry = processManager.assertProcess(instanceId);
        ProcessKey processKey = new ProcessKey(entry.instanceId(), entry.createdAt());

        checkpointManager.assertProcessAccess(entry);

        return checkpointManager.list(processKey, offset, limit);
    }

}
