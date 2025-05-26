package com.walmartlabs.concord.server;

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

import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.boot.BackgroundTasks;
import com.walmartlabs.concord.server.process.InflightProcessTracker;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.task.TaskScheduler;
import com.walmartlabs.concord.server.websocket.WebSocketChannelManager;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import static java.util.Objects.requireNonNull;

@Path("/api/v1/server")
public class ServerResource implements Resource {

    private final TaskScheduler taskScheduler;
    private final BackgroundTasks backgroundTasks;
    private final WebSocketChannelManager webSocketChannelManager;
    private final InflightProcessTracker inflightProcessTracker;
    private final DSLContext dsl;

    @Inject
    public ServerResource(TaskScheduler taskScheduler,
                          BackgroundTasks backgroundTasks,
                          WebSocketChannelManager webSocketChannelManager,
                          InflightProcessTracker inflightProcessTracker,
                          @MainDB Configuration cfg) {

        this.taskScheduler = requireNonNull(taskScheduler);
        this.backgroundTasks = requireNonNull(backgroundTasks);
        this.webSocketChannelManager = requireNonNull(webSocketChannelManager);
        this.inflightProcessTracker = requireNonNull(inflightProcessTracker);
        this.dsl = requireNonNull(cfg).dsl();
    }

    @GET
    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    public PingResponse ping() {
        dsl.selectOne().execute();
        return new PingResponse(true);
    }

    @GET
    @Path("/version")
    @Produces(MediaType.APPLICATION_JSON)
    public VersionResponse version() {
        Version v = Version.getCurrent();
        return new VersionResponse(v.getVersion(), v.getCommitId(), v.getEnv());
    }

    @POST
    @Path("/maintenance-mode")
    @Produces(MediaType.APPLICATION_JSON)
    public MaintenanceModeResponse maintenanceMode() {
        int inflightProcesses = inflightProcessTracker.getStarting() + inflightProcessTracker.getResuming();

        if (inflightProcesses > 0) {
            // do not enable the maintenance mode when processes start or resume
            // such processes will fail if the server stops
            return new MaintenanceModeResponse(false, inflightProcesses);
        }

        backgroundTasks.stop();
        webSocketChannelManager.shutdown();
        taskScheduler.stop();

        return new MaintenanceModeResponse(true, 0);
    }

    public record MaintenanceModeResponse(boolean enabled, int inflightProcesses) {
    }
}
