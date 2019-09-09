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
import com.walmartlabs.concord.server.task.TaskScheduler;
import com.walmartlabs.concord.server.websocket.WebSocketChannelManager;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Named
@Singleton
@Path("/api/v1/server")
public class ServerResource implements Resource {

    private final TaskScheduler scheduler;
    private final WebSocketChannelManager webSocketChannelManager;
    private final PingDao pingDao;

    @Inject
    public ServerResource(TaskScheduler scheduler,
                          WebSocketChannelManager webSocketChannelManager,
                          PingDao pingDao) {

        this.scheduler = scheduler;
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
    @Path("version")
    @Produces(MediaType.APPLICATION_JSON)
    public VersionResponse version() {
        Version v = Version.getCurrent();
        return new VersionResponse(v.getVersion(), v.getCommitId(), v.getEnv());
    }

    @POST
    @Path("maintenance-mode")
    public void maintenanceMode() {
        scheduler.stop();
        webSocketChannelManager.shutdown();
    }

    @Named
    public static class PingDao extends AbstractDao {

        @Inject
        public PingDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public void ping() {
            try (DSLContext tx = DSL.using(cfg)) {
                tx.selectOne().execute();
            }
        }
    }
}
