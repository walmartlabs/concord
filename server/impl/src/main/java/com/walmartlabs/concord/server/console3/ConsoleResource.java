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
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.Optional;
import java.util.Set;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;

@Path(ConsoleResource.BASE_PATH)
public class ConsoleResource implements Resource {

    public static final String BASE_PATH = "/console3";
    private static final Set<String> ALLOWED_PATHS = Set.of("index.html", "login.html");
    private static final String RESOURCE_PREFIX = "com/walmartlabs/concord/server/console3/";

    private final TemplateRenderer renderer;

    @Inject
    public ConsoleResource() {
        this.renderer = new TemplateRenderer();
    }

    @GET
    @Path("/")
    @Produces(MediaType.TEXT_HTML)
    public Response index(@Context UriInfo uriInfo) {
        var redirect = uriInfo.getBaseUriBuilder().path(BASE_PATH + "/index.html").build();
        return Response.seeOther(redirect).build();
    }

    @GET
    @Path("{path:.*}")
    @Produces(MediaType.TEXT_HTML)
    public Response index(@PathParam("path") String path,
                          @Context Optional<UserPrincipal> userPrincipal,
                          @Context HttpServletRequest request,
                          @Context HttpServletResponse response) {

        var resource = Optional.ofNullable(path)
                .filter(p -> !p.contains("..") && p.endsWith(".html"))
                .flatMap(ConsoleResource::pathToResource)
                .orElseThrow(() -> new WebApplicationException("Not found. Try starting at " + BASE_PATH, NOT_FOUND));

        var user = userPrincipal.map(UserPrincipal::getUser);
        var output = (StreamingOutput) out -> renderer.render(resource, request, response, user, out);
        return Response.ok(output)
                .build();
    }

    private static Optional<String> pathToResource(String path) {
        if (ALLOWED_PATHS.contains(path)) {
            return Optional.of(RESOURCE_PREFIX + path);
        }
        return Optional.empty();
    }
}
