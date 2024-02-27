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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.boot.BackgroundTasks;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.task.TaskScheduler;
import com.walmartlabs.concord.server.websocket.WebSocketChannelManager;
import org.jooq.Configuration;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.time.OffsetDateTime;

@Path("/api/v1/server")
public class ServerResource implements Resource {

    private final TaskScheduler taskScheduler;
    private final BackgroundTasks backgroundTasks;
    private final WebSocketChannelManager webSocketChannelManager;
    private final PingDao pingDao;

    @Inject
    public ServerResource(TaskScheduler taskScheduler,
                          BackgroundTasks backgroundTasks,
                          WebSocketChannelManager webSocketChannelManager,
                          PingDao pingDao) {

        this.taskScheduler = taskScheduler;
        this.backgroundTasks = backgroundTasks;
        this.webSocketChannelManager = webSocketChannelManager;
        this.pingDao = pingDao;
    }

    @GET
    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    public PingResponse ping() {
        pingDao.ping();
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
    public void maintenanceMode() {
        backgroundTasks.stop();

        webSocketChannelManager.shutdown();
        taskScheduler.stop();
    }

    @GET
    @Path("/test")
    @Produces(MediaType.APPLICATION_JSON)
    public TestBean test() {
        return new TestBean(OffsetDateTime.now());
    }

    @Named
    public static class PingDao extends AbstractDao {

        @Inject
        public PingDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public void ping() {
            dsl().selectOne().execute();
        }
    }

    public record TestBean(OffsetDateTime now) {
    }
}
