package com.walmartlabs.concord.console3.resources;

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

import com.walmartlabs.concord.console3.TemplateResponse;
import com.walmartlabs.concord.server.sdk.rest.Resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Map;

@Path("/console3")
@Produces(MediaType.TEXT_HTML)
public class ConsoleResource implements Resource {

    public static final String API_PREFIX = "/api/console3";

    @GET
    public Response index() {
        return get("/index.html");
    }

    @GET
    @Path("{path:.*}")
    public Response get(@PathParam("path") String path) {
        if (path == null) {
            path = "/";
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        return switch (path) {
            case "/", "/index.html" -> render(Status.OK, "index.jte");
            case "/htmx.min.js" -> serve("text/javascript", "/com/walmartlabs/concord/console3/htmx.min.js");
            case "/semantic.min.css" -> serve("text/css", "/com/walmartlabs/concord/console3/semantic.min.css");
            case "/semantic.min.js" -> serve("text/javascript", "/com/walmartlabs/concord/console3/semantic.min.js");
            default -> render(Status.NOT_FOUND, "404.jte");
        };
    }

    private Response render(Status status, String template) {
        return Response.ok(new TemplateResponse(template, Map.of()))
                .status(status)
                .build();
    }

    private Response serve(String mediaType, String resourcePath) {
        var resource = ConsoleResource.class.getResourceAsStream(resourcePath);
        if (resource == null) {
            throw new IllegalArgumentException("Resource not found, must be a bug: " + resourcePath);
        }
        return Response.ok(resource, mediaType)
                .status(Status.OK)
                .build();
    }
}
