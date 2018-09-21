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

import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

@Named
@Singleton
@Path("/api/v1/server")
public class ServerResource implements Resource {

    private final String version;
    private final String env;

    private final List<BackgroundTask> tasks;

    @Inject
    public ServerResource(List<BackgroundTask> tasks) {
        Properties props = new Properties();

        try (InputStream in = ServerResource.class.getResourceAsStream("version.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.version = props.getProperty("version");
        this.env = Utils.getEnv("CONCORD_ENV", "n/a");

        this.tasks = tasks;
    }

    @GET
    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    public PingResponse ping() {
        return new PingResponse(true);
    }

    @GET
    @Path("version")
    @Produces(MediaType.APPLICATION_JSON)
    public VersionResponse version() {
        return new VersionResponse(version, env);
    }

    @POST
    @Path("maintenance-mode")
    public void maintenanceMode() {
        tasks.forEach(BackgroundTask::stop);
    }
}
