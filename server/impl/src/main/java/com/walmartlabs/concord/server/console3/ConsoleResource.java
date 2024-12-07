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
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.Set;

@Path(ConsoleResource.BASE_PATH)
@Produces(MediaType.TEXT_HTML)
public class ConsoleResource implements Resource {

    public static final String BASE_PATH = "/console3";

    private final TemplateRenderer renderer;

    @Inject
    public ConsoleResource() {
        this.renderer = new TemplateRenderer();
    }

    /**
     * Redirect to /processes.
     */
    @GET
    @Path("/")
    public Response index(@Context UriInfo uriInfo) {
        var redirect = uriInfo.getBaseUriBuilder().path(BASE_PATH + "/processes").build();
        return Response.seeOther(redirect).build();
    }

    @POST
    @Path("/logout")
    public Response logout(@Context UriInfo uriInfo) {
        SecurityUtils.logout();
        return index(uriInfo);
    }

    /**
     * Handles all pages that are not handled by their specific @GET methods.
     * @return a rendered page
     */
    @GET
    @Path("{path:.*}")
    public Response serve(@PathParam("path") String path,
                          @Context UriInfo uriInfo,
                          @Context HttpServletRequest request,
                          @Context HttpServletResponse response) {

        var template = pathToTemplate(path);
        var templateSelectors = request.getHeader("HX-Request") != null ? Set.of("content") : Set.<String>of(); // in case of HTMX requests, render only the "content" part of the template
        var templateSpec = new TemplateSpec(template, templateSelectors, TemplateMode.HTML, null);

        var context = prepareContext(request, response);

        var output = (StreamingOutput) out -> renderer.render(templateSpec, context, out);
        return Response.ok(output)
                .build();
    }

    private static String pathToTemplate(String path) {
        if (path == null || path.contains("..")) {
            return "404.html";
        }

        return switch (path) {
            case "login" -> "login.html";
            case "processes" -> "processes.html";
            case "projects" -> "projects.html";
            default -> "404.html";
        };
    }

    private static WebContext prepareContext(HttpServletRequest request,
                                             HttpServletResponse response) {

        var app = ThymeleafApp.getInstance(request);
        var ctx = new WebContext(app.buildExchange(request, response));

        ctx.setVariable("request", request);
        ctx.setVariable("basePath", BASE_PATH);

        var principal = UserPrincipal.getCurrent();
        ctx.setVariable("user", principal != null ? principal.getUser() : null);

        return ctx;
    }
}
