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

import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.UserPrincipal;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.*;
import java.io.OutputStream;
import java.util.Optional;

@Path("/console3")
public class ConsoleResource implements Resource {

    private final TemplateRenderer renderer;

    @Inject
    public ConsoleResource() {
        this.renderer = new TemplateRenderer();
    }

    @GET
    @Path("/")
    @Produces(MediaType.TEXT_HTML)
    public Response index(@Context UriInfo uriInfo) {
        var redirect = uriInfo.getBaseUriBuilder().path("console3/index.html").build();
        return Response.seeOther(redirect).build();
    }

    @GET
    @Path("{path:.*}")
    @Produces(MediaType.TEXT_HTML)
    public Response index(@PathParam("path") String path,
                          @Context Optional<UserPrincipal> userPrincipal,
                          @Context HttpServletRequest request,
                          @Context HttpServletResponse response) {

        if (path == null || path.contains("..")) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!path.endsWith(".html")) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        var output = new StreamingOutput() {
            @Override
            public void write(OutputStream out) {
                var user = userPrincipal.map(UserPrincipal::getUser).orElse(null);
                renderer.render("com/walmartlabs/concord/server/console3/%s".formatted(path), request, response, user, out);
            }
        };

        return Response.ok(output)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .build();
    }
}
