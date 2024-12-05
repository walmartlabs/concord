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
import com.walmartlabs.concord.server.security.SecurityUtils;
import com.walmartlabs.concord.server.security.UserPrincipal;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Path(ConsoleResource.BASE_PATH)
@Produces(MediaType.TEXT_HTML)
public class ConsoleResource implements Resource {

    public static final String BASE_PATH = "/console3";
    private static final Set<String> ALLOWED_PATHS = Set.of("index.html", "login.html", "404.html", "projects.html");

    private final TemplateRenderer renderer;

    @Inject
    public ConsoleResource() {
        this.renderer = new TemplateRenderer();
    }

    @GET
    @Path("/")
    public Response index(@Context UriInfo uriInfo) {
        var redirect = createBaseUri(uriInfo, "/index.html");
        return Response.seeOther(redirect).build();
    }

    @POST
    @Path("/logout")
    public Response logout(@Context UriInfo uriInfo) {
        SecurityUtils.logout();
        return index(uriInfo);
    }

    @GET
    @Path("{path:.*}")
    public Response serve(@PathParam("path") String path,
                          @Context UriInfo uriInfo,
                          @Context Optional<UserPrincipal> userPrincipal,
                          @Context HttpServletRequest request,
                          @Context HttpServletResponse response) {

        var maybeResource = Optional.ofNullable(path)
                .filter(p -> !p.contains("..") && p.endsWith(".html"))
                .flatMap(ConsoleResource::pathToResource);

        if (maybeResource.isEmpty()) {
            var redirect = createBaseUri(uriInfo, "/404.html");
            return Response.seeOther(redirect).build();
        }

        var resource = maybeResource.get();
        var templateSelectors = request.getHeader("HX-Request") != null ? Set.of("content") : Set.<String>of();
        var user = userPrincipal.map(UserPrincipal::getUser);
        var extraVars = Map.<String, Object>of("basePath", BASE_PATH);

        var output = (StreamingOutput) out -> renderer.render(resource, templateSelectors, request, response, user, extraVars, out);
        return Response.ok(output)
                .build();
    }

    private static Optional<String> pathToResource(String path) {
        if (ALLOWED_PATHS.contains(path)) {
            return Optional.of(path);
        }
        return Optional.empty();
    }

    private static URI createBaseUri(UriInfo uriInfo, String path) {
        return uriInfo.getBaseUriBuilder().path(BASE_PATH + path).build();
    }
}
