package com.walmartlabs.concord.server.console3;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.server.process.queue.ProcessFilter;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.SecurityUtils;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.walmartlabs.concord.server.console3.ConsoleModule.BASE_PATH;
import static java.util.Objects.requireNonNull;

@Path(BASE_PATH)
@Produces(MediaType.TEXT_HTML)
public class ConsoleResource implements Resource {

    private final ProcessQueueDao queueDao;

    @Inject
    public ConsoleResource(ProcessQueueDao queueDao) {
        this.queueDao = requireNonNull(queueDao);
    }

    /**
     * Main page. Redirects to /processes.
     */
    @GET
    @Path("/")
    public Response index(@Context UriInfo uriInfo) {
        var redirect = uriInfo.getBaseUriBuilder().path(BASE_PATH + "/processes").build();
        return Response.seeOther(redirect).build();
    }

    /**
     * Perform logout and redirect back to the main page.
     */
    @POST
    @Path("/logout")
    public Response logout(@Context UriInfo uriInfo) {
        SecurityUtils.logout();
        return index(uriInfo);
    }

    /**
     * Process list.
     */
    @GET
    @Path("/processes")
    public TemplateResponse processes() {
        var rows = queueDao.list(ProcessFilter.builder().build());
        return new TemplateResponse("processes", Map.of("processes", rows));
    }

    /**
     * Process details.
     */
    @GET
    @Path("/process/{instanceId}")
    public TemplateResponse process(@PathParam("instanceId") UUID instanceId) {
        var maybeProcess = queueDao.get(PartialProcessKey.from(instanceId));
        return Optional.ofNullable(maybeProcess)
                .map(process -> new TemplateResponse("process", Map.of("process", process)))
                .orElseGet(this::serve404);
    }

    /**
     * Handles 404s.
     */
    @GET
    @Path("{path:.*}")
    public TemplateResponse serve404() {
        return new TemplateResponse("404.html");
    }
}
