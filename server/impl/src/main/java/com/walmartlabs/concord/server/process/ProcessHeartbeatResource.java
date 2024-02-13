package com.walmartlabs.concord.server.process;

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

import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import java.util.UUID;

@Path("/api/v1/process")
@Tag(name = "ProcessHeartbeat")
public class ProcessHeartbeatResource implements Resource {

    private final ProcessQueueDao queueDao;

    @Inject
    public ProcessHeartbeatResource(ProcessQueueDao queueDao) {
        this.queueDao = queueDao;
    }

    @POST
    @Path("{id}/ping")
    @Operation(description = "Process heartbeat", operationId = "pingProcess")
    public void ping(@PathParam("id") UUID instanceId) {
        if (!queueDao.touch(instanceId)) {
            throw new IllegalArgumentException("Process not found: " + instanceId);
        }
    }
}
