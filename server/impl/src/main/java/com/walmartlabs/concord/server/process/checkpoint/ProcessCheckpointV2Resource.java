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
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
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
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Named
@Singleton
@Api(value = "CheckpointV2", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@javax.ws.rs.Path("/api/v2/process")
public class ProcessCheckpointV2Resource implements Resource {
    private static final Logger log = LoggerFactory.getLogger(ProcessCheckpointV2Resource.class);

    private static final String RESTORE_PROCESS_ACTION = "restore";

    private final ProcessManager processManager;
    private final ProcessCheckpointManager checkpointManager;

    @Inject
    public ProcessCheckpointV2Resource(ProcessManager processManager,
                                       ProcessCheckpointManager checkpointManager) {

        this.processManager = processManager;
        this.checkpointManager = checkpointManager;
    }

    @GET
    @ApiOperation(value = "Process checkpoint")
    @javax.ws.rs.Path("{id}/checkpoint")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public GenericCheckpointResponse processCheckpoint(@ApiParam @PathParam("id") UUID instanceId,
                                                       @ApiParam @QueryParam("name") String checkpointName,
                                                       @ApiParam @QueryParam("action") String action) {

        ProcessEntry entry = processManager.assertProcess(instanceId);
        ProcessKey processKey = new ProcessKey(entry.instanceId(), entry.createdAt());

        switch (action) {
            case RESTORE_PROCESS_ACTION: {
                return restore(processKey, checkpointName);
            }
            default: {
                throw new ConcordApplicationException("Invalid action type: " + action);
            }
        }
    }

    private GenericCheckpointResponse restore(ProcessKey processKey, String checkpointName) {
        log.info("Restoring process from checkpoint: {}", checkpointName);

        UUID checkpointId = checkpointManager.getRecentCheckpointId(processKey, checkpointName);
        processManager.restoreFromCheckpoint(processKey, checkpointId);

        return new GenericCheckpointResponse();
    }

}
